package com.example.findme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.AppWidgetId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.dataStore
import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.widget.ConnectionWidget
import com.example.findme.widget.WidgetStore
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real RemoteViews and independent selections without contacting the backend. */
@SuppressLint("RestrictedApi")
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class StatusWidgetRenderingTest {
    @Test fun rendersBothSizesAndIndependentPeople() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ownId = AppWidgetId(-100001)
        val otherId = AppWidgetId(-100002)
        val manager = GlanceAppWidgetManager(context)
        val ownKey = WidgetStore.selectionKey(manager.getAppWidgetId(ownId))
        val otherKey = WidgetStore.selectionKey(manager.getAppWidgetId(otherId))
        val keys = listOf(WidgetStore.cacheKey, WidgetStore.errorKey, ownKey, otherKey)
        val original = context.dataStore.data.first()
        try {
            WidgetStore.publish(context, "widget-test-owner",
                listOf(AccountProfile("widget-test-owner", "Yo"), AccountProfile("widget-test-person", "Ana")),
                emptyList(), listOf(StatusOverrideDto("test", "widget-test-person", true, "En clase",
                    expiresAt = Instant.now().plusSeconds(3600).toString())))
            WidgetStore.select(context, manager.getAppWidgetId(ownId), "widget-test-owner", "widget-test-owner")
            WidgetStore.select(context, manager.getAppWidgetId(otherId), "widget-test-owner", "widget-test-person")
            for ((id, size, expected, filename) in listOf(
                RenderCase(ownId, DpSize(250.dp, 170.dp), "Libre", "widget-compact.png"),
                RenderCase(otherId, DpSize(320.dp, 220.dp), "En clase", "widget-expanded.png")
            )) {
                val remoteViews = ConnectionWidget().compose(context, id = id, size = size)
                instrumentation.runOnMainSync {
                    val density = context.resources.displayMetrics.density
                    val width = (size.width.value * density).toInt()
                    val height = (size.height.value * density).toInt()
                    val host = FrameLayout(context)
                    val view = remoteViews.apply(context, host)
                    host.addView(view)
                    host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    host.layout(0, 0, width, height)
                    val text = allText(host)
                    assertTrue("Expected $expected in rendered widget", text.contains(expected))
                    assertTrue("Each widget must retain its person", text.contains(if (expected == "Libre") "Mi estado" else "Ana"))
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    host.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null), filename).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            context.dataStore.edit { prefs -> keys.forEach { key ->
                original[key]?.let { prefs[key] = it } ?: prefs.remove(key)
            } }
        }
    }

    private data class RenderCase(val id: androidx.glance.GlanceId, val size: DpSize, val expected: String, val filename: String)
    private fun allText(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString(" ") { allText(view.getChildAt(it)) }
        else -> ""
    }
}
