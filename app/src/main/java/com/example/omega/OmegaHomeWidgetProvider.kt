package com.example.omega

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class OmegaHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.omega_home_widget)
            val voicePi = buildPi(context, 5001, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_START_LISTENING, true))
            val visionPi = buildPi(context, 5002, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CAMERA, true))
            val dashPi = buildPi(context, 5003, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DASHBOARD, true))

            views.setOnClickPendingIntent(R.id.widget_orb_icon, voicePi)
            views.setOnClickPendingIntent(R.id.widget_voice_btn, voicePi)
            views.setOnClickPendingIntent(R.id.widget_vision_btn, visionPi)
            views.setOnClickPendingIntent(R.id.widget_routine_btn, dashPi)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildPi(context: Context, code: Int, intent: Intent): PendingIntent {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
