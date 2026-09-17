package com.blockveil.tracker.remover.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.blockveil.tracker.remover.R
import com.blockveil.tracker.remover.TrackerRemoverApp
import com.blockveil.tracker.remover.ui.MainActivity
import com.blockveil.tracker.remover.ui.WidgetCleanActivity

class TrackerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun buildViews(context: Context): RemoteViews {
            val settings = (context.applicationContext as TrackerRemoverApp).settings
            val views = RemoteViews(context.packageName, R.layout.widget_tracker_remover)

            views.setTextViewText(R.id.widgetLinksCount, settings.totalLinksCleaned.toString())
            views.setTextViewText(R.id.widgetTrackersCount, settings.totalTrackersRemoved.toString())

            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent)

            // A more specific child-view click target always wins over the
            // root's, so tapping this button runs the clean action instead
            // of just opening the app.
            val cleanIntent = PendingIntent.getActivity(
                context,
                1,
                Intent(context, WidgetCleanActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetCleanButton, cleanIntent)

            return views
        }
    }
}
