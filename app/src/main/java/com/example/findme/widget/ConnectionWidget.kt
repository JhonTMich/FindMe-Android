package com.example.findme.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    override val sizeMode = SizeMode.Exact

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
            val size = LocalSize.current
            val expanded = size.height >= 170.dp
            val scene = state?.scene ?: WidgetScene.FREE
            val palette = WidgetPalette.of(scene)
            val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
            val bold = FontWeight.Bold
            val iconSize = if (expanded) 36.dp else 30.dp
            Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(palette.surface)).clickable(openApp)
                .padding(start = 16.dp, end = 10.dp, top = if (expanded) 12.dp else 8.dp, bottom = if (expanded) 12.dp else 8.dp)) {
                // Header: who is shown, with the actions on the right.
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(person?.let { if (it.id == data?.owner) "Mi estado" else it.displayName } ?: "FindMe",
                        GlanceModifier.defaultWeight().clickable(actionStartActivity(config)),
                        style = TextStyle(color = ColorProvider(palette.primary), fontSize = if (expanded) 17.sp else 14.sp, fontWeight = bold), maxLines = 1)
                    Image(ImageProvider(R.drawable.widget_ic_refresh), "Actualizar",
                        GlanceModifier.size(iconSize).padding(6.dp).clickable(actionRunCallback<RefreshConnectionWidget>()),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.primary)))
                    Image(ImageProvider(R.drawable.widget_ic_more), "Configurar",
                        GlanceModifier.size(iconSize).padding(6.dp).clickable(actionStartActivity(config)),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.primary)))
                }
                // Status on the left, the whole illustration on the right, both above the next-activity card.
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Box(GlanceModifier.background(ImageProvider(palette.chip))
                            .padding(start = 10.dp, end = 11.dp, top = 2.dp, bottom = 4.dp)) {
                            Text(state?.label ?: if (data == null) "Sin sesión" else "Sin persona",
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = if (expanded) 13.sp else 11.sp, fontWeight = bold), maxLines = 1)
                        }
                        Text(state?.title ?: if (data == null) "Abre FindMe" else "Elige una persona",
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = if (expanded) 30.sp else 21.sp, fontWeight = bold),
                            maxLines = 1)
                        if (expanded) {
                            Text(state?.detail ?: if (data == null) "Inicia sesión para ver tu estado" else "Toca ⋮ para configurar",
                                style = TextStyle(color = ColorProvider(palette.secondary), fontSize = 13.sp, fontWeight = bold), maxLines = 2)
                        }
                    }
                    Image(ImageProvider(palette.art), null, GlanceModifier.fillMaxHeight().width(size.width * 0.42f),
                        contentScale = ContentScale.Fit)
                }
                Spacer(GlanceModifier.height(if (expanded) 8.dp else 4.dp))
                Row(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_card))
                    .padding(horizontal = 12.dp, vertical = if (expanded) 8.dp else 4.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_ic_calendar), null, GlanceModifier.size(if (expanded) 24.dp else 18.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.secondary)))
                    Spacer(GlanceModifier.width(10.dp))
                    Box(GlanceModifier.width(1.dp).height(if (expanded) 30.dp else 18.dp).background(palette.divider)) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        if (expanded) Text("Próxima actividad",
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = 13.sp, fontWeight = bold), maxLines = 1)
                        Text(state?.next?.let { if (expanded) it else "Próxima: $it" } ?: "Configura tu widget",
                            style = TextStyle(color = ColorProvider(palette.secondary), fontSize = if (expanded) 12.sp else 11.sp, fontWeight = bold),
                            maxLines = 1)
                    }
                }
            }
        }
    }
}


/** Colors and shapes picked to stay readable over each illustrated background. */
private data class WidgetPalette(
    val surface: Int, val art: Int, val chip: Int,
    val primary: Color, val secondary: Color, val accent: Color, val divider: Color
) {
    companion object {
        fun of(scene: WidgetScene) = when (scene) {
            WidgetScene.WORK -> WidgetPalette(R.drawable.widget_surface_work, R.drawable.widget_art_work, R.drawable.widget_chip_work,
                Color(0xFF33205C), Color(0xFF55407F), Color(0xFF5A2DA8), Color(0x667448C2))
            WidgetScene.UNIVERSITY -> WidgetPalette(R.drawable.widget_surface_university, R.drawable.widget_art_university, R.drawable.widget_chip_university,
                Color(0xFF0C2E4E), Color(0xFF2C5073), Color(0xFF0F5799), Color(0x661C6FBF))
            WidgetScene.FREE -> WidgetPalette(R.drawable.widget_surface_free, R.drawable.widget_art_free, R.drawable.widget_chip_free,
                Color(0xFF45200B), Color(0xFF6E3B20), Color(0xFF9E420A), Color(0x66C75A1A))
        }
    }
}

class RefreshConnectionWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        GlanceWidgetWorker.enqueueRefresh(context)
    }
}
