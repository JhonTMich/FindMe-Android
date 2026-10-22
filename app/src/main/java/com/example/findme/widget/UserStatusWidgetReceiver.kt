package com.example.findme.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class UserStatusWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = UserStatusWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlanceWidgetWorker.enqueuePeriodicUpdate(context)
    }
}
