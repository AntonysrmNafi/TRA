package com.blockveil.tracker.remover.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around SharedPreferences for the app's settings toggles and
 * API keys. Plain SharedPreferences (not encrypted) is a deliberate,
 * simple choice for v1; see the README for the note on upgrading this to
 * EncryptedSharedPreferences if that matters for your use case.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bv_settings", Context.MODE_PRIVATE)

    var safeBrowsingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAFE_BROWSING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SAFE_BROWSING_ENABLED, value).apply()

    var safeBrowsingApiKey: String
        get() = prefs.getString(KEY_SAFE_BROWSING_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SAFE_BROWSING_KEY, value).apply()

    var virusTotalEnabled: Boolean
        get() = prefs.getBoolean(KEY_VT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VT_ENABLED, value).apply()

    var virusTotalApiKey: String
        get() = prefs.getString(KEY_VT_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VT_KEY, value).apply()

    var domainAgeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOMAIN_AGE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DOMAIN_AGE_ENABLED, value).apply()

    var saveHistory: Boolean
        get() = prefs.getBoolean(KEY_SAVE_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_HISTORY, value).apply()

    /** One of "system", "light", "dark". */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /** Lifetime total of links cleaned. Never decreases, even if history entries are deleted. */
    var totalLinksCleaned: Int
        get() = prefs.getInt(KEY_TOTAL_LINKS_CLEANED, 0)
        private set(value) = prefs.edit().putInt(KEY_TOTAL_LINKS_CLEANED, value).apply()

    /** Lifetime total of individual trackers removed. Never decreases, even if history entries are deleted. */
    var totalTrackersRemoved: Int
        get() = prefs.getInt(KEY_TOTAL_TRACKERS_REMOVED, 0)
        private set(value) = prefs.edit().putInt(KEY_TOTAL_TRACKERS_REMOVED, value).apply()

    /**
     * Call once per successfully cleaned link (whether or not history saving
     * is on). These are lifetime usage counters, separate from the history
     * table, so clearing or swiping away history entries never reduces them.
     */
    fun recordCleanedLink(trackerCount: Int) {
        totalLinksCleaned += 1
        totalTrackersRemoved += trackerCount
    }

    /** True if at least one online safety check is turned on. */
    fun anySafetyCheckEnabled(): Boolean =
        safeBrowsingEnabled || virusTotalEnabled || domainAgeEnabled

    companion object {
        private const val KEY_SAFE_BROWSING_ENABLED = "safe_browsing_enabled"
        private const val KEY_SAFE_BROWSING_KEY = "safe_browsing_key"
        private const val KEY_VT_ENABLED = "virustotal_enabled"
        private const val KEY_VT_KEY = "virustotal_key"
        private const val KEY_DOMAIN_AGE_ENABLED = "domain_age_enabled"
        private const val KEY_SAVE_HISTORY = "save_history"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TOTAL_LINKS_CLEANED = "total_links_cleaned"
        private const val KEY_TOTAL_TRACKERS_REMOVED = "total_trackers_removed"
    }
}
