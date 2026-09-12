package com.blockveil.tracker.remover.util

import androidx.appcompat.app.AppCompatDelegate

/** Maps the stored "system"/"light"/"dark" setting to AppCompatDelegate's night-mode constants. */
object ThemeMode {

    fun apply(mode: String) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    private fun toNightMode(mode: String): Int = when (mode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
