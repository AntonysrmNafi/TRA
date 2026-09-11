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
    }
}
