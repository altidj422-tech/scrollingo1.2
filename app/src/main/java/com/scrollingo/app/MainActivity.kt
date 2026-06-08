package com.scrollingo.app

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.util.function.Consumer

class MainActivity : AppCompatActivity() {

    private lateinit var languageContainer: LinearLayout
    private lateinit var appsContainer: LinearLayout
    private lateinit var thresholdValue: TextView
    private lateinit var thresholdCaption: TextView
    private lateinit var btnMinus: TextView
    private lateinit var btnPlus: TextView
    private lateinit var pillAccessibility: TextView
    private lateinit var pillOverlay: TextView
    private lateinit var appearanceContainer: LinearLayout
    private lateinit var pillNotification: TextView

    private val languageChips = LinkedHashMap<String, View>()
    private val appChips = LinkedHashMap<String, View>()
    private val themeChips = LinkedHashMap<Int, View>()

    private var threshold = Prefs.DEFAULT_THRESHOLD

    private val previewOverlay by lazy { QuizOverlayManager(applicationContext) }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) StatsNotifier.update(this)
            // Offer the QS tile only after the notification dialog has resolved,
            // so the two system prompts don't stack on top of each other.
            maybeOfferQuickTile()
        }

    private val flags = mapOf(
        "Spanish" to "🇪🇸",
        "French" to "🇫🇷",
        "German" to "🇩🇪",
        "Italian" to "🇮🇹",
        "Portuguese" to "🇵🇹"
    )

    private val appEmoji = mapOf(
        TargetApps.TIKTOK.id to "🎵",
        TargetApps.INSTAGRAM.id to "📸"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        languageContainer = findViewById(R.id.language_container)
        appsContainer = findViewById(R.id.apps_container)
        thresholdValue = findViewById(R.id.threshold_value)
        thresholdCaption = findViewById(R.id.threshold_caption)
        btnMinus = findViewById(R.id.btn_threshold_minus)
        btnPlus = findViewById(R.id.btn_threshold_plus)
        pillAccessibility = findViewById(R.id.pill_accessibility)
        pillOverlay = findViewById(R.id.pill_overlay)
        appearanceContainer = findViewById(R.id.appearance_container)
        pillNotification = findViewById(R.id.pill_notification)

        buildLanguageChips()
        buildAppChips()
        buildAppearanceChips()
        setupThreshold()
        setupButtons()
        setupNotificationToggle()

        // Refresh word packs over WiFi in the background (no-op if no host set).
        WordPackSync.enqueue(applicationContext)

        // Ask for notifications first; if no dialog is needed, go straight to
        // offering the Quick Settings tile.
        if (!maybeRequestNotificationPermission()) maybeOfferQuickTile()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        // Keep the status-bar tracker in step with the latest stats.
        if (QuizProgress.snapshot(this).hasActivity) StatsNotifier.update(this)
    }

    /** @return true if a permission dialog was launched (caller should wait for it). */
    private fun maybeRequestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        // Don't nag for notifications the user has switched off in-app.
        if (!Prefs.isStatsNotificationEnabled(this)) return false
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return false
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    /**
     * Ask the system to drop Scrollingo's tile straight into Quick Settings with a
     * single "Add" tap — no digging through the QS edit screen. Android forbids
     * adding a tile without that one confirmation, so this is as automatic as it
     * gets. Fires once; some heavily-customized skins (e.g. older MIUI) may ignore
     * the request, in which case the tile can still be added manually via Edit.
     */
    private fun maybeOfferQuickTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (Prefs.wasTilePrompted(this)) return
        val statusBar = getSystemService(StatusBarManager::class.java) ?: return
        statusBar.requestAddTileService(
            ComponentName(this, ScrollingoTileService::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, R.drawable.ic_tile_cat),
            mainExecutor,
            Consumer { result -> onTileAddResult(result) }
        )
    }

    private fun onTileAddResult(result: Int) {
        when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                Prefs.setTilePrompted(this, true)
                Toast.makeText(this, "Scrollingo added to Quick Settings", Toast.LENGTH_SHORT).show()
            }
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> {
                // User declined — respect it and don't ask again.
                Prefs.setTilePrompted(this, true)
            }
            // Any error (e.g. a skin that doesn't support the request) is left
            // un-flagged so we can try again on a future launch.
        }
    }

    // ---- Language ----

    private fun buildLanguageChips() {
        val current = Prefs.getLanguage(this)
        languageContainer.removeAllViews()
        languageChips.clear()

        for (language in Dictionary.languages) {
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.chip_selectable)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(12)
                layoutParams = lp
            }

            chip.addView(TextView(this).apply {
                text = flags[language] ?: "🌐"
                textSize = 30f
                gravity = Gravity.CENTER
            })
            chip.addView(TextView(this).apply {
                text = language
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_dark))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(4)
                layoutParams = lp
            })

            chip.isSelected = language == current
            chip.setOnClickListener { selectLanguage(language) }

            languageContainer.addView(chip)
            languageChips[language] = chip
        }
    }

    private fun selectLanguage(language: String) {
        Prefs.setLanguage(this, language)
        languageChips.forEach { (lang, chip) -> chip.isSelected = lang == language }
    }

    // ---- Apps ----

    private fun buildAppChips() {
        val enabled = Prefs.getEnabledAppIds(this)
        appsContainer.removeAllViews()
        appChips.clear()

        for (app in TargetApps.ALL) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.chip_selectable)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(12)
                layoutParams = lp
            }

            row.addView(TextView(this).apply {
                text = appEmoji[app.id] ?: "📱"
                textSize = 22f
            })
            row.addView(TextView(this).apply {
                text = app.label
                textSize = 16f
                setTextColor(getColor(R.color.text_dark))
                val lp = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                lp.marginStart = dp(14)
                layoutParams = lp
            })
            val check = TextView(this).apply {
                text = "✓"
                textSize = 20f
                setTextColor(getColor(R.color.blue))
            }
            row.addView(check)

            val isOn = app.id in enabled
            row.isActivated = isOn
            check.visibility = if (isOn) View.VISIBLE else View.INVISIBLE

            row.setOnClickListener {
                val nowOn = !row.isActivated
                row.isActivated = nowOn
                check.visibility = if (nowOn) View.VISIBLE else View.INVISIBLE
                saveAppChoices()
            }

            appsContainer.addView(row)
            appChips[app.id] = row
        }
    }

    private fun saveAppChoices() {
        val ids = appChips.filterValues { it.isActivated }.keys.toSet()
        Prefs.setEnabledAppIds(this, ids)
    }

    // ---- Appearance ----

    private fun buildAppearanceChips() {
        val current = Prefs.getThemeMode(this)
        appearanceContainer.removeAllViews()
        themeChips.clear()

        val options = listOf(
            Prefs.THEME_SYSTEM to "System",
            Prefs.THEME_LIGHT to "Light",
            Prefs.THEME_DARK to "Dark"
        )

        options.forEachIndexed { index, (mode, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_dark))
                setBackgroundResource(R.drawable.chip_selectable)
                setPadding(dp(14), dp(16), dp(14), dp(16))
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                if (index < options.lastIndex) lp.marginEnd = dp(12)
                layoutParams = lp
            }
            chip.isSelected = mode == current
            chip.setOnClickListener { selectThemeMode(mode) }

            appearanceContainer.addView(chip)
            themeChips[mode] = chip
        }
    }

    private fun selectThemeMode(mode: Int) {
        if (mode == Prefs.getThemeMode(this)) return
        Prefs.setThemeMode(this, mode)
        themeChips.forEach { (m, chip) -> chip.isSelected = m == mode }
        // Applies immediately; AppCompat recreates this Activity for the new config.
        AppCompatDelegate.setDefaultNightMode(ScrollingoApp.nightModeFlag(mode))
    }

    // ---- Notifications ----

    private fun setupNotificationToggle() {
        renderNotificationPill()
        pillNotification.setOnClickListener { toggleStatsNotification() }
    }

    private fun renderNotificationPill() {
        val on = Prefs.isStatsNotificationEnabled(this)
        pillNotification.text = if (on) "ON" else "OFF"
        pillNotification.setBackgroundResource(if (on) R.drawable.pill_on else R.drawable.pill_off)
        pillNotification.setTextColor(getColor(if (on) R.color.purple_dark else R.color.red_dark))
    }

    private fun toggleStatsNotification() {
        val turningOn = !Prefs.isStatsNotificationEnabled(this)
        Prefs.setStatsNotificationEnabled(this, turningOn)
        renderNotificationPill()
        if (turningOn) {
            // If we still need the runtime permission, ask; the result callback
            // posts the tracker. Otherwise show it right away.
            if (!maybeRequestNotificationPermission()) StatsNotifier.update(this)
        } else {
            StatsNotifier.cancel(this)
        }
    }

    // ---- Threshold ----

    private fun setupThreshold() {
        threshold = Prefs.getThreshold(this)
        renderThreshold()
        btnMinus.setOnClickListener { changeThreshold(-1) }
        btnPlus.setOnClickListener { changeThreshold(+1) }
    }

    private fun changeThreshold(delta: Int) {
        val next = (threshold + delta).coerceIn(Prefs.MIN_THRESHOLD, Prefs.MAX_THRESHOLD)
        if (next == threshold) return
        threshold = next
        Prefs.setThreshold(this, threshold)
        renderThreshold()
    }

    private fun renderThreshold() {
        thresholdValue.text = threshold.toString()
        thresholdCaption.text = if (threshold == 1) "video" else "videos"
        setStepperEnabled(btnMinus, threshold > Prefs.MIN_THRESHOLD)
        setStepperEnabled(btnPlus, threshold < Prefs.MAX_THRESHOLD)
    }

    private fun setStepperEnabled(button: TextView, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.4f
        button.setTextColor(getColor(if (enabled) R.color.blue else R.color.text_hint))
    }

    // ---- Buttons / permissions ----

    private fun setupButtons() {
        findViewById<View>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find Scrollingo in the list and turn it on", Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.btn_overlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<View>(R.id.btn_preview).setOnClickListener { previewQuiz() }
    }

    private fun previewQuiz() {
        if (!previewOverlay.canDrawOverlays()) {
            Toast.makeText(this, "Allow \"display over other apps\" first", Toast.LENGTH_LONG).show()
            return
        }
        if (previewOverlay.isShowing) return

        val language = Prefs.getLanguage(this)
        val words = runCatching { Dictionary.load(this, language) }.getOrDefault(emptyList())
        if (words.size < 2) {
            Toast.makeText(this, "No words available for $language", Toast.LENGTH_SHORT).show()
            return
        }
        val correct = QuizScheduler.nextWord(this, language, words)
        previewOverlay.show(
            language,
            QuizGenerator.generate(words, correct),
            onResult = { firstTryCorrect ->
                QuizScheduler.record(this, language, correct.word, firstTryCorrect)
                StatsNotifier.update(this, QuizProgress.record(this, firstTryCorrect))
            }
        ) {}
    }

    private fun refreshPermissionStatus() {
        setPill(pillAccessibility, isAccessibilityServiceEnabled())
        setPill(pillOverlay, previewOverlay.canDrawOverlays())
    }

    private fun setPill(pill: TextView, on: Boolean) {
        pill.text = if (on) "ON" else "OFF"
        pill.setBackgroundResource(if (on) R.drawable.pill_on else R.drawable.pill_off)
        pill.setTextColor(getColor(if (on) R.color.purple_dark else R.color.red_dark))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, ScrollDetectorService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component == expected) return true
        }
        return false
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
