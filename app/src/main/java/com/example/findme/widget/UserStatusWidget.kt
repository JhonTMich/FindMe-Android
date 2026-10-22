package com.example.findme.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.findme.data.local.UserPreferencesRepository
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        UserStatusWidget().updateAll(context)
    }
}

class UserStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val userSession = try {
            UserPreferencesRepository(context).getUserSession()
        } catch (_: Exception) {
            null
        }

        val displayName = userSession?.displayName ?: "Usuario"

        val status = if (userSession != null) {
            try {
                StatusRepository.getDirectCurrentStatus(
                    userId = userSession.userId,
                    displayName = userSession.displayName
                )
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        provideContent {
            val (locationText, subtitleText, cardBgColor, categoryLabel) = when (status) {
                is UserCurrentStatus.InClass -> {
                    Quadruple(
                        "Universidad UPAO",
                        "${status.currentSchedule.title} • ${status.currentSchedule.location}",
                        Color(0xFFFFE0B2),
                        "📚 EN CLASE"
                    )
                }
                is UserCurrentStatus.Override -> {
                    Quadruple(
                        status.customLocation,
                        status.customMessage ?: "Aviso especial activo",
                        Color(0xFFEDE7F6),
                        "📌 AVISO"
                    )
                }
                is UserCurrentStatus.FreeTime -> {
                    Quadruple(
                        "En Casa / Tiempo Libre",
                        "Sin clases en este momento",
                        Color(0xFFE8F5E9),
                        "🏠 LIBRE"
                    )
                }
                else -> {
                    Quadruple(
                        if (userSession == null) "Sin vincular" else "Cargando...",
                        if (userSession == null) "Abre la app para vincular con un código" else "Consultando ubicación...",
                        Color(0xFFF5F5F5),
                        "● EN VIVO"
                    )
                }
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBgColor)
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.Start
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.Start
                ) {
                    Text(
                        text = categoryLabel,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color(0xFF424242))
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Button(
                        text = "🔄",
                        onClick = actionRunCallback<RefreshWidgetAction>()
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                Text(
                    text = "$displayName está en:",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(Color(0xFF616161))
                    )
                )

                Text(
                    text = locationText,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFF212121))
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = subtitleText,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(Color(0xFF424242))
                    )
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
