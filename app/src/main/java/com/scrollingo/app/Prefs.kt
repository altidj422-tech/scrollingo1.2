package com.scrollingo.app

import android.content.Context

/** Tiny SharedPreferences wrapper holding the user's setup choices. */
object Prefs {
    private const val FILE = "scrollingo_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_APP_IDS = "app_ids"
    private const val KEY_PAUSED = "paused"
    private const val KEY_TILE_PROMPTED = "tile_prompted"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_STATS_NOTIFICATION = "stats_notification"

    const val MIN_THRESHOLD = 1
    const val MAX_THRESHOLD = 20
    const val DEFAULT_THRESHOLD = 5

    /** Appearance options, stored as plain ints so [Prefs] needs no AppCompat dep. */
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getLanguage(c: Context): String =
        sp(c).getString(KEY_LANGUAGE, Dictionary.DEFAULT_LANGUAGE) ?: Dictionary.DEFAULT_LANGUAGE

    fun setLanguage(c: Context, value: String) =
        sp(c).edit().putString(KEY_LANGUAGE, value).apply()

    fun getThreshold(c: Context): Int =
        sp(c).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    fun setThreshold(c: Context, value: Int) =
        sp(c).edit().putInt(KEY_THRESHOLD, value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)).apply()

    fun getEnabledAppIds(c: Context): Set<String> =
        sp(c).getStringSet(KEY_APP_IDS, TargetApps.ALL_IDS) ?: TargetApps.ALL_IDS

    fun setEnabledAppIds(c: Context, value: Set<String>) =
        sp(c).edit().putStringSet(KEY_APP_IDS, value).apply()

    /** When paused (via the Quick Settings tile), scroll detection stops firing quizzes. */
    fun isPaused(c: Context): Boolean =
        sp(c).getBoolean(KEY_PAUSED, false)

    fun setPaused(c: Context, value: Boolean) =
        sp(c).edit().putBoolean(KEY_PAUSED, value).apply()

    /** Whether we've already offered to auto-add the Quick Settings tile (so we don't nag). */
    fun wasTilePrompted(c: Context): Boolean =
        sp(c).getBoolean(KEY_TILE_PROMPTED, false)

    fun setTilePrompted(c: Context, value: Boolean) =
        sp(c).edit().putBoolean(KEY_TILE_PROMPTED, value).apply()

    /** Appearance: [THEME_SYSTEM] (default), [THEME_LIGHT], or [THEME_DARK]. */
    fun getThemeMode(c: Context): Int =
        sp(c).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(c: Context, value: Int) =
        sp(c).edit().putInt(KEY_THEME_MODE, value).apply()

    /** Whether the quiet status-bar progress notification is shown. On by default. */
    fun isStatsNotificationEnabled(c: Context): Boolean =
        sp(c).getBoolean(KEY_STATS_NOTIFICATION, true)

    fun setStatsNotificationEnabled(c: Context, value: Boolean) =
        sp(c).edit().putBoolean(KEY_STATS_NOTIFICATION, value).apply()
}
