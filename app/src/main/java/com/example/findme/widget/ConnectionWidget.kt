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
            val synced = data?.let {
                val time = java.time.Instant.ofEpochMilli(it.syncedAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                if (prefs.get(WidgetStore.errorKey) != null) "Sin conexión · $time" else "Actualizado $time"
            } ?: "Toca ⋮ para configurar"
            Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp)
                .background(ImageProvider(palette.background), contentScale = ContentScale.Crop)
                .clickable(openApp).padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)) {
                // Header: who is shown and when the data was fetched, with the actions on the right.
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Top) {
                    Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(config))) {
                        Text(person?.let { if (it.id == data?.owner) "Mi estado" else it.displayName } ?: "FindMe",
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(synced, style = TextStyle(color = ColorProvider(palette.muted), fontSize = 10.sp), maxLines = 1)
                    }
                    Image(ImageProvider(R.drawable.widget_ic_refresh), "Actualizar",
                        GlanceModifier.size(36.dp).padding(7.dp).clickable(actionRunCallback<RefreshConnectionWidget>()),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.primary)))
                    Image(ImageProvider(R.drawable.widget_ic_more), "Configurar",
                        GlanceModifier.size(36.dp).padding(7.dp).clickable(actionStartActivity(config)),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.primary)))
                }
                // Status: text on the left half, the illustration fills the right half.
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(state?.label ?: if (data == null) "Sin sesión" else "Sin persona",
                            GlanceModifier.background(ImageProvider(palette.chip)).padding(horizontal = 10.dp, vertical = 3.dp),
                            style = TextStyle(color = ColorProvider(palette.accent), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Spacer(GlanceModifier.height(if (expanded) 6.dp else 2.dp))
                        Text(state?.title ?: if (data == null) "Abre FindMe" else "Elige una persona",
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = if (expanded) 28.sp else 22.sp,
                                fontWeight = FontWeight.Bold), maxLines = if (expanded) 2 else 1)
                        if (expanded) {
                            Text(state?.detail ?: if (data == null) "Inicia sesión para ver tu estado" else "Toca ⋮ para configurar",
                                style = TextStyle(color = ColorProvider(palette.secondary), fontSize = 12.sp), maxLines = 2)
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                }
                // Next activity card across the bottom.
                Row(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_card))
                    .padding(horizontal = 12.dp, vertical = if (expanded) 8.dp else 5.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_ic_calendar), null,
                        GlanceModifier.size(if (expanded) 24.dp else 20.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.secondary)))
                    Spacer(GlanceModifier.width(10.dp))
                    Box(GlanceModifier.width(1.dp).height(if (expanded) 30.dp else 22.dp).background(palette.divider)) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        if (expanded) Text("Próxima actividad",
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(state?.next?.let { if (expanded) it else "Próxima: $it" } ?: "Configura tu widget",
                            style = TextStyle(color = ColorProvider(palette.secondary), fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Colors and shapes picked to stay readable over each illustrated background. */
private data class WidgetPalette(
    val background: Int, val chip: Int,
    val primary: Color, val secondary: Color, val accent: Color, val muted: Color, val divider: Color
) {
    companion object {
        fun of(scene: WidgetScene) = when (scene) {
            WidgetScene.WORK -> WidgetPalette(R.drawable.widget_scene_work, R.drawable.widget_chip_work,
                Color(0xFF3B2667), Color(0xFF5B4789), Color(0xFF6A3DB8), Color(0xFF7A6A9E), Color(0x667448C2))
            WidgetScene.UNIVERSITY -> WidgetPalette(R.drawable.widget_scene_university, R.drawable.widget_chip_university,
                Color(0xFF0F3558), Color(0xFF34587C), Color(0xFF1765B0), Color(0xFF5A7A99), Color(0x661C6FBF))
            WidgetScene.FREE -> WidgetPalette(R.drawable.widget_scene_free, R.drawable.widget_chip_free,
                Color(0xFF4A230D), Color(0xFF7A4326), Color(0xFFB4500F), Color(0xFF8E6048), Color(0x66C75A1A))
        }
    }
}

class RefreshConnectionWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        GlanceWidgetWorker.enqueueRefresh(context)
    }
}
