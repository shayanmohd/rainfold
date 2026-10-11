package com.mohdshayan.rainfold.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.data.PlaybackStatus
import com.mohdshayan.rainfold.di.ServiceLocator
import com.mohdshayan.rainfold.ui.EngineLabels
import com.mohdshayan.rainfold.ui.resume.ResumeActivity
import kotlinx.coroutines.launch

/**
 * The home-screen widget, plain RemoteViews: the last mix's name and one button that resumes it (or
 * pauses it while it plays). Taps go through ResumeActivity so playback starts from the foreground.
 */
class RainfoldWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ServiceLocator.init(context)
        ServiceLocator.appScope.launch {
            try {
                push(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            ServiceLocator.init(app)
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, RainfoldWidget::class.java))
            if (ids.isEmpty()) return
            ServiceLocator.appScope.launch { push(app, manager, ids) }
        }

        private suspend fun push(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val live = ServiceLocator.session.load()
            val playing = ServiceLocator.session.status.value == PlaybackStatus.PLAYING
            val tap = PendingIntent.getActivity(
                context, 1,
                Intent(context, ResumeActivity::class.java).setAction(ResumeActivity.ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_resume).apply {
                    setTextViewText(R.id.widget_title, EngineLabels.title(context, live))
                    setTextViewText(
                        R.id.widget_state,
                        context.getString(if (playing) R.string.widget_playing else R.string.widget_resume),
                    )
                    setImageViewResource(R.id.widget_button, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                    setContentDescription(
                        R.id.widget_button,
                        context.getString(if (playing) R.string.action_pause else R.string.widget_resume),
                    )
                    setOnClickPendingIntent(R.id.widget_root, tap)
                    setOnClickPendingIntent(R.id.widget_button, tap)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
