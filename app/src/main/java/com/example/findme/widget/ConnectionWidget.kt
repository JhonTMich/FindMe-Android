package com.example.findme.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.text.FontWeight
import com.example.findme.R
import com.example.findme.MainActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.findme.data.local.dataStore
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ConnectionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConnectionWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        GlanceWidgetWorker.enqueuePeriodicUpdate(context)
        GlanceWidgetWorker.enqueueRefresh(context)
    }
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val work = androidx.work.WorkManager.getInstance(context)
        work.cancelUniqueWork("GlanceWidgetUpdateWorker")
        work.cancelUniqueWork("WidgetManualRefresh")
        work.cancelUniqueWork("WidgetStatusBoundary")
    }
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlanceWidgetWorker.enqueuePeriodicUpdate(context)
        GlanceWidgetWorker.enqueueRefresh(context)
    }
}

class ConnectionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 170.dp), DpSize(320.dp, 220.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialPreferences = context.dataStore.data.first()
        provideContent {
            val prefs by context.dataStore.data.collectAsState(initial = initialPreferences)
            val data = WidgetStore.decode(prefs.get(WidgetStore.cacheKey))
            val selected = prefs.get(WidgetStore.selectionKey(widgetId))?.split("|", limit = 2)
            val legacyPerson = if (prefs.get(stringPreferencesKey("connection_widget_owner")) == data?.owner)
                prefs.get(stringPreferencesKey("connection_widget_person")) else null
            val personId = if (selected?.firstOrNull() == data?.owner) selected?.getOrNull(1)
                else if (selected == null) legacyPerson ?: data?.owner else null
            val person = data?.profiles?.find { it.id == personId }
            val now = ZonedDateTime.now(runCatching { ZoneId.of(person?.timezone) }.getOrDefault(ZoneId.systemDefault()))
            val state = person?.let { widgetStatus(it.displayName,
                data.schedules.filter { row -> row.userId == it.id },
                data.statuses.filter { row -> row.userId == it.id }, now) }
            val config = Intent(context, StatusWidgetConfigurationActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("findme://widget/$widgetId"))
            val expanded = LocalSize.current.height >= 220.dp
            val green = Color(0xFFACFA62)
            val accent = Color(0xFF0D1328)
            Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background)).padding(14.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(person?.let { if (it.id == data?.owner) "Mi estado" else it.displayName } ?: "FindMe · Estado actual",
                        GlanceModifier.defaultWeight().clickable(actionStartActivity(config)),
                        style = TextStyle(color = ColorProvider(Color(0xFFF8F9FF)), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text("↻", GlanceModifier.clickable(actionRunCallback<RefreshConnectionWidget>()).padding(horizontal = 10.dp, vertical = 4.dp),
                        style = TextStyle(color = ColorProvider(green), fontSize = 20.sp))
                    Text("⋮", GlanceModifier.clickable(actionStartActivity(config)).padding(horizontal = 6.dp, vertical = 4.dp),
                        style = TextStyle(color = ColorProvider(green), fontSize = 20.sp))
                }
                Spacer(GlanceModifier.height(8.dp))
                Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity(Intent(context, MainActivity::class.java))).background(ImageProvider(R.drawable.widget_status_background))
                    .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Image(ImageProvider(when (state?.kind) {
                            "activity" -> R.drawable.widget_school
                            "work" -> R.drawable.widget_work
                            "shared" -> R.drawable.widget_chat
                            else -> R.drawable.widget_free
                        }), contentDescription = null, modifier = GlanceModifier.size(if (expanded) 38.dp else 28.dp))
                        Spacer(GlanceModifier.width(12.dp))
                        Text(state?.title ?: if (data == null) "Abre FindMe" else "Elige una persona",
                            style = TextStyle(color = ColorProvider(accent), fontSize = if (expanded) 27.sp else 22.sp,
                                fontWeight = FontWeight.Bold), maxLines = if (expanded) 2 else 1)
                    }
                    if (expanded) {
                        Spacer(GlanceModifier.height(6.dp))
                        Text(state?.detail ?: "Inicia sesión y configura tu widget",
                            style = TextStyle(color = ColorProvider(Color(0xFF243A2A)), fontSize = 12.sp), maxLines = 2)
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_calendar), null, GlanceModifier.size(20.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text("Próxima: ${state?.next ?: "Configura tu estado"}",
                        style = TextStyle(color = ColorProvider(Color(0xFFF8F9FF)), fontSize = 12.sp), maxLines = 2)
                }
                Spacer(GlanceModifier.height(4.dp))
                    Text(data?.let {
                        val time = java.time.Instant.ofEpochMilli(it.syncedAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                        if (prefs.get(WidgetStore.errorKey) != null) "Sin conexión · Datos del $time"
                        else "Datos del $time"
                    } ?: "Toca ⋮ para configurar",
                        style = TextStyle(color = ColorProvider(Color(0xFFB8C4DF)), fontSize = if (expanded) 10.sp else 9.sp), maxLines = 1)
            }
        }
    }
}

class RefreshConnectionWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        GlanceWidgetWorker.enqueueRefresh(context)
    }
}
