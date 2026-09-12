package com.blockveil.tracker.remover

import android.app.Application
import com.blockveil.tracker.remover.data.AppDatabase
import com.blockveil.tracker.remover.data.SettingsRepository
import com.blockveil.tracker.remover.util.ThemeMode

class TrackerRemoverApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Read directly via a throwaway SettingsRepository instead of the
        // lazy `settings` property, since applying the theme mode this
        // early is what avoids a light->dark flash on cold start.
        ThemeMode.apply(SettingsRepository(this).themeMode)
    }
}
