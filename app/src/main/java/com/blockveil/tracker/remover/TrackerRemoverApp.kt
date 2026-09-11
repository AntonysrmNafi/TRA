package com.blockveil.tracker.remover

import android.app.Application
import com.blockveil.tracker.remover.data.AppDatabase
import com.blockveil.tracker.remover.data.SettingsRepository

class TrackerRemoverApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
}
