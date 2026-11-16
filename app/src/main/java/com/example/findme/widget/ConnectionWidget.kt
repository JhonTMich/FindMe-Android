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
            val scene = state?.scene ?: WidgetScene.FREE
            val palette = WidgetPalette.of(scene)
            val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
            Box(GlanceModifier.fillMaxSize().cornerRadius(24.dp)
                .background(ImageProvider(palette.background), contentScale = ContentScale.Crop)
                .clickable(openApp)) {
                Row(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // The illustration occupies the right half, so all the text lives on the left.
                    Column(GlanceModifier.fillMaxHeight().defaultWeight()) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(person?.let { if (it.id == data?.owner) "Mi estado" else it.displayName } ?: "FindMe",
                                GlanceModifier.defaultWeight().clickable(actionStartActivity(config)),
                                style = TextStyle(color = ColorProvider(palette.primary), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text("↻", GlanceModifier.clickable(actionRunCallback<RefreshConnectionWidget>()).padding(horizontal = 6.dp),
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                            Text("⋮", GlanceModifier.clickable(actionStartActivity(config)).padding(start = 4.dp, end = 2.dp),
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        Text(state?.label ?: if (data == null) "Sin sesión" else "Sin persona",
                            style = TextStyle(color = ColorProvider(palette.accent), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(state?.title ?: if (data == null) "Abre FindMe" else "Elige una persona",
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = if (expanded) 24.sp else 20.sp,
                                fontWeight = FontWeight.Bold), maxLines = 2)
                        Text(state?.detail ?: if (data == null) "Inicia sesión para ver tu estado" else "Toca ⋮ para configurar",
                            style = TextStyle(color = ColorProvider(palette.secondary), fontSize = 12.sp), maxLines = if (expanded) 2 else 1)
                        Spacer(GlanceModifier.defaultWeight())
                        state?.let {
                            Text("Próxima: ${it.next}",
                                style = TextStyle(color = ColorProvider(palette.secondary), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                maxLines = if (expanded) 2 else 1)
                        }
                        data?.let {
                            val time = java.time.Instant.ofEpochMilli(it.syncedAt).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                            Text(if (prefs.get(WidgetStore.errorKey) != null) "Sin conexión · $time" else "Actualizado $time",
                                style = TextStyle(color = ColorProvider(palette.muted), fontSize = 9.sp), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.fillMaxHeight().defaultWeight())
                }
            }
        }
    }
}

/** Text colors picked to stay readable over each illustrated background. */
private data class WidgetPalette(val background: Int, val primary: Color, val secondary: Color, val accent: Color, val muted: Color) {
    companion object {
        fun of(scene: WidgetScene) = when (scene) {
            WidgetScene.WORK -> WidgetPalette(R.drawable.widget_scene_work,
                Color(0xFF3B2667), Color(0xFF5B4789), Color(0xFF7448C2), Color(0xFF7A6A9E))
            WidgetScene.UNIVERSITY -> WidgetPalette(R.drawable.widget_scene_university,
                Color(0xFF0F3558), Color(0xFF34587C), Color(0xFF1C6FBF), Color(0xFF5A7A99))
            WidgetScene.FREE -> WidgetPalette(R.drawable.widget_scene_free,
                Color(0xFF5A2A10), Color(0xFF80472A), Color(0xFFC75A1A), Color(0xFF96664A))
        }
    }
}

class RefreshConnectionWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        GlanceWidgetWorker.enqueueRefresh(context)
    }
}
