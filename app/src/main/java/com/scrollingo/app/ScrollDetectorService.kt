package com.scrollingo.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Watches scroll events inside the user's chosen social apps and counts how many
 * NEW videos the user has swiped past. Swiping forward adds to the count; swiping
 * back subtracts from it — and the balance is NOT floored at zero. So if you scroll
 * back over 100 videos the count becomes -100, and you then have to swipe forward
 * past all 100 again (re-watching, not new) PLUS [threshold] more before the quiz
 * fires. That guarantees the quiz only ever appears on genuinely new videos. Once it
 * fires, the balance resets to zero.
 *
 * How a "video" is counted, most-reliable signal first:
 *  1. PIXEL DELTA. scrollDeltaY is the physical truth about which way the content
 *     moved: positive = swiped to the NEXT video (+1), negative = swiped BACK (−1).
 *     We sum it over the swipe and round to whole screen-heights, ignoring travel
 *     too short to be a real swipe ([MIN_TRAVEL_FRACTION]) so taps/jitter don't
 *     count. The SIGN is what makes back-swipes subtract.
 *  2. PAGE INDEX (fallback). For feeds that report no pixel delta at all, both apps
 *     are vertical pagers (RecyclerView/ViewPager) that expose the current page via
 *     fromIndex/toIndex; we count the signed change between swipes instead.
 *
 * A single fling emits a burst of TYPE_VIEW_SCROLLED events, so we wait for the
 * burst to go quiet for [BURST_GAP_MS] and resolve it once — collapsing one
 * gesture into one net step (or several, for a long fling past many videos).
 *
 * Scrolling anything that ISN'T the full-screen video feed must NOT count — the
 * Explore/Search grid, a profile grid, the comments sheet, DM/inbox lists, chat
 * threads. Each app has its OWN feed detector so that hardening one can't regress
 * the other: TikTok uses [looksLikeListOrSheet], Instagram uses
 * [looksLikeInstagramNonFeed]. Both reject on positive evidence only (a multi-column
 * grid, several rows visible at once, or a scroller that doesn't fill the screen),
 * so the single-item feed is never mistaken for one. The Instagram detector
 * additionally ignores collectionInfo.rowCount, because the Reels RecyclerView
 * reports a large one that would otherwise reject the whole feed.
 */
class ScrollDetectorService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlay by lazy { QuizOverlayManager(this) }

    private var enabledPackages: Set<String> = emptySet()
    private var threshold: Int = Prefs.DEFAULT_THRESHOLD

    private var currentPackage: String? = null

    /**
     * Running signed scroll balance since the last quiz. Forward swipes raise it,
     * back swipes lower it (it CAN go negative), and the quiz fires when it reaches
     * [threshold]. Negative means you've scrolled back over old videos and must pass
     * them again before any new one counts.
     */
    private var netScrolls: Int = 0

    /** Last feed page index we've seen ([UNKNOWN] until the first feed swipe). */
    private var lastIndex: Int = UNKNOWN

    /** Most recent feed page index seen during the in-flight burst. */
    private var burstFeedIndex: Int = UNKNOWN

    /** Signed pixels accumulated for the in-flight swipe burst (index-less fallback). */
    private var burstDeltaY: Int = 0

    /** Latches true if any event in the burst looks like a comment/DM/grid scroll. */
    private var burstRejected: Boolean = false

    /** Height of one "video" in px — a full screen height. */
    private val videoHeightPx: Int by lazy { resources.displayMetrics.heightPixels }

    /** Full screen width in px — a feed page spans all of it; grid tiles don't. */
    private val videoWidthPx: Int by lazy { resources.displayMetrics.widthPixels }

    private val finalizeBurst = Runnable { finalizeBurst() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        reloadConfig()
        Log.i(TAG, "Service connected. threshold=$threshold packages=$enabledPackages")
    }

    private fun reloadConfig() {
        threshold = Prefs.getThreshold(this)
        enabledPackages = TargetApps.packagesFor(Prefs.getEnabledAppIds(this))
    }

    /** True when the on-screen app is Instagram (any of its package variants). */
    private fun isInstagram(pkg: String): Boolean = pkg in TargetApps.INSTAGRAM.packages

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg == packageName) return // ignore our own UI/overlay
                val switchingApps = pkg != currentPackage
                currentPackage = pkg
                if (pkg in enabledPackages) {
                    // Pick up any settings the user changed since last time.
                    reloadConfig()
                    // Only start a fresh session when actually entering the app —
                    // NOT on every in-app screen change (TikTok fires these as you
                    // swipe, and wiping state here was eating back-swipes). Index
                    // discontinuities from a real pager switch are handled in
                    // finalizeBurst() via the plausibility guard instead.
                    if (switchingApps) resetCounters()
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val pkg = event.packageName?.toString() ?: currentPackage ?: return
                handleScroll(pkg, event)
            }
        }
    }

    private fun handleScroll(pkg: String, event: AccessibilityEvent) {
        if (pkg !in enabledPackages) return
        if (Prefs.isPaused(this)) return // user paused Scrollingo from the Quick Settings tile
        if (overlay.isShowing) return

        // Each app gets its own feed detector. TikTok's (looksLikeListOrSheet) is
        // left exactly as-is because it's working; Instagram needs its own, since
        // the Reels RecyclerView reports a large collectionInfo.rowCount that the
        // TikTok detector wrongly treats as a "list" and rejects.
        val nonFeed =
            if (isInstagram(pkg)) looksLikeInstagramNonFeed(event)
            else looksLikeListOrSheet(event)
        if (nonFeed) {
            // Comments / DMs / chats / grids — latch the burst so it never counts.
            burstRejected = true
        } else {
            // A feed-like scroll: remember the page index if it gives us one, and
            // also accumulate raw pixels in case this feed never exposes an index.
            val idx = feedIndexOf(event)
            if (idx != UNKNOWN) burstFeedIndex = idx
            val dy = event.scrollDeltaY
            if (dy != 0 && dy != Int.MIN_VALUE) burstDeltaY += dy
        }

        // (Re)arm the quiet-timer: keep extending the burst while events arrive,
        // and resolve the whole gesture once at the end.
        mainHandler.removeCallbacks(finalizeBurst)
        mainHandler.postDelayed(finalizeBurst, BURST_GAP_MS)
    }

    /**
     * The current page index for a feed-like scroll, or [UNKNOWN] if this event
     * doesn't look like a single-item pager. A full-screen video pager shows one
     * page (fromIndex == toIndex) or two mid-transition (span 1); anything wider is
     * a list and is handled by [looksLikeListOrSheet] instead.
     */
    private fun feedIndexOf(event: AccessibilityEvent): Int {
        val from = event.fromIndex
        val to = event.toIndex
        if (from < 0) return UNKNOWN
        if (to < from || to - from > FEED_INDEX_SPAN) return UNKNOWN
        return from
    }

    /**
     * True when the scroll almost certainly came from something OTHER than the
     * full-screen video feed: a comment/DM list, a chat thread, the Instagram
     * Explore/Search grid, or a profile grid.
     *
     * The video feed shows exactly ONE full-screen page at a time. Everything we
     * want to ignore instead shows many items at once — either as a multi-column
     * grid or as a list of smaller rows — or it doesn't fill the screen (the
     * comments sheet sits over the lower half). Every test rejects on POSITIVE
     * evidence only, so an unknown feed scroller is never mistaken for a list and
     * TikTok/Reels counting can't regress.
     */
    private fun looksLikeListOrSheet(event: AccessibilityEvent): Boolean {
        // Many items visible at once => a list of comments / DMs / chats, or a grid.
        // The feed is a single full-screen page, so its visible span is 0–1.
        val from = event.fromIndex
        val to = event.toIndex
        if (from in 0..to && to - from >= LIST_VISIBLE_SPAN) {
            Log.d(TAG, "feedcheck reject(list-span) from=$from to=$to cls=${event.className}")
            return true
        }

        val node = event.source ?: return false
        return try {
            val info = node.collectionInfo
            val cols = info?.columnCount ?: -1
            val rows = info?.rowCount ?: -1

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            // A scroller that doesn't fill the screen (e.g. the comments bottom
            // sheet sitting over the lower half) is not the feed.
            val tooShort =
                bounds.height() in 1 until (videoHeightPx * MIN_FEED_HEIGHT_FRACTION).toInt()
            val startsLow = bounds.top > videoHeightPx * MAX_FEED_TOP_FRACTION
            // A grid (Explore / Search / profile) shows several NARROW tiles side by
            // side; the feed page is always full-width, so this can't misfire on it.
            val narrow = countNarrowTiles(node, GRID_MIN_TILES)

            val reject = cols > 1 ||                 // multi-column grid
                rows > LIST_VISIBLE_SPAN ||          // collection with many rows
                tooShort || startsLow ||             // comments / DM sheet
                narrow >= GRID_MIN_TILES             // grid of narrow tiles

            Log.d(
                TAG,
                "feedcheck cls=${event.className} from=$from to=$to dy=${event.scrollDeltaY} " +
                    "cols=$cols rows=$rows children=${node.childCount} narrow=$narrow " +
                    "bounds=${bounds.width()}x${bounds.height()}@top${bounds.top} -> reject=$reject"
            )
            reject
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    /**
     * Instagram-only feed test — a separate system from TikTok's [looksLikeListOrSheet]
     * so that hardening one can't regress the other.
     *
     * Instagram's Reels feed is a full-screen vertical pager just like TikTok's, but
     * its RecyclerView reports a large collectionInfo.rowCount (every loaded reel).
     * The TikTok detector rejects "many rows", so it killed Reels counting entirely.
     * Here we IGNORE rowCount and reject only on evidence that genuinely can't be a
     * single full-screen reel:
     *   • several list rows visible at once  -> comments / DMs / chat threads
     *   • a multi-column collection          -> Explore / profile grid
     *   • several narrow tiles side by side  -> Search / Explore grid (width-based;
     *                                            catches grids even when columnCount
     *                                            isn't reported on the scroll event)
     *   • a scroller that doesn't fill the screen -> the comments bottom sheet
     * A real Reels swipe trips none of these, so it counts; everything else is dropped.
     */
    private fun looksLikeInstagramNonFeed(event: AccessibilityEvent): Boolean {
        // Many items visible at once => a vertical LIST (comments / DMs / chat), not
        // the Reels pager (which shows exactly one full-screen reel: span 0–1).
        val from = event.fromIndex
        val to = event.toIndex
        if (from in 0..to && to - from >= LIST_VISIBLE_SPAN) {
            Log.d(TAG, "ig feedcheck reject(list-span) from=$from to=$to cls=${event.className}")
            return true
        }

        val node = event.source ?: return false
        return try {
            val info = node.collectionInfo
            val cols = info?.columnCount ?: -1

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            // The comments sheet sits over the lower part of the screen, so it's
            // shorter than a full reel. (A reel page fills the screen.)
            val tooShort =
                bounds.height() in 1 until (videoHeightPx * MIN_FEED_HEIGHT_FRACTION).toInt()
            // A grid (Explore / Search / profile) shows several NARROW tiles side by
            // side; a reel page is always full-width, so this can't misfire on it.
            val narrow = countNarrowTiles(node, GRID_MIN_TILES)

            // rowCount is deliberately NOT consulted — Reels reports a big one.
            val reject = cols > 1 || tooShort || narrow >= GRID_MIN_TILES

            Log.d(
                TAG,
                "ig feedcheck cls=${event.className} from=$from to=$to dy=${event.scrollDeltaY} " +
                    "cols=$cols rows=${info?.rowCount ?: -1} children=${node.childCount} narrow=$narrow " +
                    "bounds=${bounds.width()}x${bounds.height()}@top${bounds.top} -> reject=$reject"
            )
            reject
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    /**
     * Counts up to [limit] of [node]'s direct children that are visible and clearly
     * NARROWER than the screen — the signature of a grid (Explore, Search, profile
     * thumbnails). The full-screen video feed is one full-WIDTH page, so it never
     * trips this; that's why width, not height, is the feed-safe discriminator.
     */
    private fun countNarrowTiles(node: AccessibilityNodeInfo, limit: Int): Int {
        val total = node.childCount
        if (total <= 0) return 0
        val maxTileWidth = (videoWidthPx * GRID_TILE_MAX_WIDTH_FRACTION).toInt()
        var narrow = 0
        val b = Rect()
        for (i in 0 until total) {
            val child = node.getChild(i) ?: continue
            try {
                child.getBoundsInScreen(b)
                val onScreen = !b.isEmpty && b.bottom > 0 && b.top < videoHeightPx
                if (onScreen && b.width() in 1 until maxTileWidth) {
                    narrow++
                    if (narrow >= limit) return narrow
                }
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return narrow
    }

    /** Called once a swipe burst has gone quiet — turn it into a net video count. */
    private fun finalizeBurst() {
        val rejected = burstRejected
        val idx = burstFeedIndex
        val delta = burstDeltaY
        burstRejected = false
        burstFeedIndex = UNKNOWN
        burstDeltaY = 0

        // Came from comments / DMs / a chat / a grid — never a video.
        if (rejected) {
            Log.d(TAG, "ignored burst (non-feed scroller)")
            return
        }

        // Signed page-index change, but only when it's plausible. A real swipe moves
        // a few pages; a huge jump means a different pager (e.g. tapping into a
        // profile's reels), so we re-baseline instead of counting it.
        var indexDelta: Int? = null
        if (idx != UNKNOWN) {
            if (lastIndex != UNKNOWN) {
                val d = idx - lastIndex
                if (abs(d) <= MAX_PLAUSIBLE_STEP) indexDelta = d
            }
            lastIndex = idx
        }

        // scrollDeltaY is the physical truth about direction: positive = content
        // moved up = swiped to the NEXT video, negative = swiped BACK. We only honor
        // travel long enough to be a real swipe (not a tap/jitter), then round it to
        // whole pages (min one, carrying the sign).
        val pixelSteps: Int =
            if (abs(delta) >= videoHeightPx * MIN_TRAVEL_FRACTION) {
                (delta.toFloat() / videoHeightPx).roundToInt()
                    .let { if (it == 0) (if (delta > 0) 1 else -1) else it }
            } else 0

        // Prefer the pixel signal (unambiguous direction); fall back to the page
        // index for feeds that report no pixel delta at all.
        val steps = when {
            pixelSteps != 0 -> pixelSteps
            indexDelta != null -> indexDelta
            else -> 0
        }
        if (steps == 0) return

        // No flooring: back-swipes drive the balance negative, so re-scrolling old
        // videos only climbs back to where you were — the quiz waits for NEW ground.
        netScrolls += steps
        Log.d(TAG, "burst steps=$steps (pixels=$pixelSteps indexD=$indexDelta idx=$idx delta=$delta) -> net=$netScrolls/$threshold")

        if (netScrolls >= threshold) {
            netScrolls = 0
            showQuiz()
        }
    }

    /** Drop the in-flight burst without counting it (e.g. on a screen change). */
    private fun cancelBurst() {
        mainHandler.removeCallbacks(finalizeBurst)
        burstDeltaY = 0
        burstRejected = false
        burstFeedIndex = UNKNOWN
    }

    private fun resetCounters() {
        cancelBurst()
        netScrolls = 0
        lastIndex = UNKNOWN
    }

    private fun showQuiz() {
        if (!overlay.canDrawOverlays()) {
            Log.w(TAG, "Overlay permission missing; skipping quiz")
            return
        }
        val language = Prefs.getLanguage(this)
        val words = runCatching { Dictionary.load(this, language) }.getOrElse {
            Log.e(TAG, "Failed to load dictionary for $language", it)
            emptyList()
        }
        if (words.size < 2) return

        val correct = QuizScheduler.nextWord(this, language, words)
        val quiz = QuizGenerator.generate(words, correct)
        mainHandler.post {
            overlay.show(
                language,
                quiz,
                onResult = { firstTryCorrect ->
                    QuizScheduler.record(this, language, correct.word, firstTryCorrect)
                    StatsNotifier.update(this, QuizProgress.record(this, firstTryCorrect))
                }
            ) {
                // Solved: nothing else to do, counting resumes automatically.
                Log.d(TAG, "Quiz solved")
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        mainHandler.removeCallbacks(finalizeBurst)
        overlay.dismiss()
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "Scrollingo"

        /** Sentinel for "no page index known yet". */
        private const val UNKNOWN = -1

        /** A swipe burst is "done" once no scroll event arrives for this long. */
        private const val BURST_GAP_MS = 220L

        /**
         * Widest fromIndex→toIndex span still treated as the single-item feed (0 =
         * one page settled, 1 = two pages mid-transition). Anything wider is a list.
         */
        private const val FEED_INDEX_SPAN = 1

        /**
         * Largest page-index jump we still treat as real swiping. A bigger jump
         * means the index belongs to a different pager (profile reels, a reload),
         * so we re-baseline rather than count it.
         */
        private const val MAX_PLAUSIBLE_STEP = 20

        /**
         * Minimum burst travel (fraction of screen height) before a pixel delta
         * counts as a swipe. Low enough to catch apps that under-report distance,
         * high enough to ignore taps and the tiny jitter of a playing video.
         */
        private const val MIN_TRAVEL_FRACTION = 0.2f

        /** Reject the burst if this many list rows (or more) are visible at once. */
        private const val LIST_VISIBLE_SPAN = 4

        /** This many narrow visible tiles side by side marks a grid, not a feed. */
        private const val GRID_MIN_TILES = 3

        /** A child narrower than this fraction of screen width is a grid tile. */
        private const val GRID_TILE_MAX_WIDTH_FRACTION = 0.6f

        /** A feed scroller must be at least this tall (fraction of the screen). */
        private const val MIN_FEED_HEIGHT_FRACTION = 0.7f

        /** A feed scroller must start within this fraction from the screen top. */
        private const val MAX_FEED_TOP_FRACTION = 0.22f
    }
}
