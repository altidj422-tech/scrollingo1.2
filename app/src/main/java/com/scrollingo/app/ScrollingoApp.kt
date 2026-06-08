package com.scrollingo.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the user's saved appearance choice as early as possible — before any
 * Activity is created — so light/dark is correct on first frame. Runs in every
 * process (UI + accessibility service), keeping them in sync.
 */
class ScrollingoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(nightModeFlag(Prefs.getThemeMode(this)))
    }

    companion object {
        /** Map a [Prefs] theme constant to the matching AppCompat night-mode flag. */
        fun nightModeFlag(mode: Int): Int = when (mode) {
            Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
