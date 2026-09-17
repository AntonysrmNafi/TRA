package com.blockveil.tracker.remover.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Pushes fresh stat numbers to every placed instance of the home-screen widget. */
object WidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TrackerWidgetProvider::class.java))
        for (id in ids) {
            manager.updateAppWidget(id, TrackerWidgetProvider.buildViews(context))
        }
    }
}
