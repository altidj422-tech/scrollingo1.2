package com.scrollingo.app

import android.content.Context
import java.time.LocalDate

/**
 * Local-only record of how the learner is doing, powering the status-bar tracker:
 *  - today's accuracy (correct vs. total first-try answers, resets at midnight),
 *  - the current run of consecutive correct answers ("streak"),
 *  - the best streak ever reached.
 *
 * Everything lives in SharedPreferences on the device. Nothing is uploaded — the
 * tracker only ever reflects answers the learner gave on this phone.
 */
object QuizProgress {

    private const val PREFS = "quiz_progress"

    private const val KEY_DAY = "day"            // epoch-day the today-counters belong to
    private const val KEY_TODAY_CORRECT = "today_correct"
    private const val KEY_TODAY_TOTAL = "today_total"
    private const val KEY_STREAK = "streak"      // current consecutive-correct run
    private const val KEY_BEST = "best_streak"

    data class Snapshot(
        val todayCorrect: Int,
        val todayTotal: Int,
        val streak: Int,
        val bestStreak: Int
    ) {
        /** Today's first-try accuracy as a 0..100 percentage (0 when nothing answered). */
        val todayPercent: Int
            get() = if (todayTotal == 0) 0 else Math.round(todayCorrect * 100f / todayTotal)

        val hasActivity: Boolean get() = todayTotal > 0 || streak > 0 || bestStreak > 0
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): Long = LocalDate.now().toEpochDay()

    /**
     * Record one answer's first-try outcome and return the fresh snapshot.
     * Rolls the daily counters over automatically when the date changes.
     */
    fun record(context: Context, correct: Boolean): Snapshot {
        val p = prefs(context)
        val today = today()

        var todayCorrect = p.getInt(KEY_TODAY_CORRECT, 0)
        var todayTotal = p.getInt(KEY_TODAY_TOTAL, 0)
        if (p.getLong(KEY_DAY, Long.MIN_VALUE) != today) {
            // New day (or first ever answer): start the daily tally fresh.
            todayCorrect = 0
            todayTotal = 0
        }

        todayTotal += 1
        var streak = p.getInt(KEY_STREAK, 0)
        var best = p.getInt(KEY_BEST, 0)
        if (correct) {
            todayCorrect += 1
            streak += 1
            if (streak > best) best = streak
        } else {
            streak = 0
        }

        p.edit()
            .putLong(KEY_DAY, today)
            .putInt(KEY_TODAY_CORRECT, todayCorrect)
            .putInt(KEY_TODAY_TOTAL, todayTotal)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_BEST, best)
            .apply()

        return Snapshot(todayCorrect, todayTotal, streak, best)
    }

    /** Read the current stats without changing them (rolls the day over if stale). */
    fun snapshot(context: Context): Snapshot {
        val p = prefs(context)
        val sameDay = p.getLong(KEY_DAY, Long.MIN_VALUE) == today()
        val todayCorrect = if (sameDay) p.getInt(KEY_TODAY_CORRECT, 0) else 0
        val todayTotal = if (sameDay) p.getInt(KEY_TODAY_TOTAL, 0) else 0
        return Snapshot(
            todayCorrect = todayCorrect,
            todayTotal = todayTotal,
            streak = p.getInt(KEY_STREAK, 0),
            bestStreak = p.getInt(KEY_BEST, 0)
        )
    }
}
