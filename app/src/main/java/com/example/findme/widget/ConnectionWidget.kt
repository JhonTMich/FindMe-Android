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
            val scene = state?.scene ?: WidgetScene.FREE
            val palette = WidgetPalette.of(scene)
            // Sizes and texts are fitted to the real cell (4×2 or 4×3) and the system font scale.
            val layout = WidgetLayout.fit(size.width.value, size.height.value, context.resources.configuration.fontScale,
                WidgetTexts(
                    header = person?.let { if (it.id == data?.owner) "Mi estado" else it.displayName } ?: "Near",
                    label = state?.label ?: if (data == null) "Sin sesión" else "Sin persona",
                    title = state?.title ?: if (data == null) "Abre Near" else "Elige una persona",
                    detail = state?.detail ?: if (data == null) "Inicia sesión para ver tu estado" else "Toca ⋮ para configurar",
                    next = state?.next ?: "Configura tu widget",
                    nextIsActivity = state != null))
            val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
            val bold = FontWeight.Bold
            Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(palette.surface)).clickable(openApp)
                .padding(start = layout.paddingStart.dp, end = layout.paddingEnd.dp,
                    top = layout.paddingVertical.dp, bottom = layout.paddingVertical.dp)) {
                // Who is shown and their status on the left; the illustration takes the whole height down to
                // the card on the right, so the spare room sits beside the art instead of under the status.
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.Top) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(layout.header, GlanceModifier.fillMaxWidth().clickable(actionStartActivity(config)),
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = layout.headerSize.sp, fontWeight = bold), maxLines = 1)
                        Spacer(GlanceModifier.height(layout.headerGap.dp))
                        val label = layout.label
                        if (label != null) {
                            Box(GlanceModifier.background(ImageProvider(palette.chip))
                                .padding(start = 10.dp, end = 11.dp, top = 2.dp, bottom = 4.dp)) {
                                Text(label, style = TextStyle(color = ColorProvider(palette.accent), fontSize = layout.chipSize.sp, fontWeight = bold), maxLines = 1)
                            }
                            Spacer(GlanceModifier.height(4.dp))
                        }
                        Text(layout.title,
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = layout.titleSize.sp, fontWeight = bold),
                            maxLines = layout.titleLines)
                        val detail = layout.detail
                        if (detail != null) {
                            Spacer(GlanceModifier.height(2.dp))
                            Text(detail,
                                style = TextStyle(color = ColorProvider(palette.secondary), fontSize = layout.detailSize.sp, fontWeight = bold),
                                maxLines = layout.detailLines)
                        }
                    }
                    if (layout.artWidth > 0f) {
                        Spacer(GlanceModifier.width(6.dp))
                        Image(ImageProvider(palette.art), null, GlanceModifier.fillMaxHeight().width(layout.artWidth.dp),
                            contentScale = ContentScale.Fit)
                    }
                }
                Spacer(GlanceModifier.height(layout.gap.dp))
                Row(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_card))
                    .padding(start = 12.dp, end = 4.dp, top = layout.cardPadding.dp, bottom = layout.cardPadding.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_ic_calendar), null, GlanceModifier.size(layout.calendarSize.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.secondary)))
                    Spacer(GlanceModifier.width(10.dp))
                    Box(GlanceModifier.width(1.dp).height(if (layout.nextLabel != null) 30.dp else 18.dp).background(palette.divider)) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        val nextLabel = layout.nextLabel
                        if (nextLabel != null) Text(nextLabel,
                            style = TextStyle(color = ColorProvider(palette.primary), fontSize = layout.nextLabelSize.sp, fontWeight = bold), maxLines = 1)
                        Text(layout.next,
                            style = TextStyle(color = ColorProvider(palette.secondary), fontSize = layout.nextSize.sp, fontWeight = bold),
                            maxLines = 1)
                    }
                    // Actions live in the card so the illustration can use the top-right corner.
                    Image(ImageProvider(R.drawable.widget_ic_refresh), "Actualizar",
                        GlanceModifier.size(layout.iconSize.dp).padding(layout.iconPadding.dp)
                            .clickable(actionRunCallback<RefreshConnectionWidget>()),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.primary)))
                    Image(ImageProvider(R.drawable.widget_ic_more), "Configurar",
                        GlanceModifier.size(layout.iconSize.dp).padding(layout.iconPadding.dp).clickable(actionStartActivity(config)),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.primary)))
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
