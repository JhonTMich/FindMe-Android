@file:Suppress("RestrictedApi")

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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.findme.data.local.UserPreferencesRepository
import com.example.findme.data.repository.StatusRepository

class ControlOnTheWayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val userPrefsRepo = UserPreferencesRepository(context)
        val session = userPrefsRepo.getUserSession()
        val isAdmin = userPrefsRepo.isAdminRole()
        if (isAdmin && session != null) {
            StatusRepository.setOnTheWayOverride(session.userId)
            UserStatusWidget().updateAll(context)
            FindMeControlWidget().updateAll(context)
        }
    }
}

class ControlArrivedHomeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val userPrefsRepo = UserPreferencesRepository(context)
        val session = userPrefsRepo.getUserSession()
        val isAdmin = userPrefsRepo.isAdminRole()
        if (isAdmin && session != null) {
            StatusRepository.clearOverrides(session.userId)
            UserStatusWidget().updateAll(context)
            FindMeControlWidget().updateAll(context)
        }
    }
}

class FindMeControlWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val userPrefsRepo = UserPreferencesRepository(context)
        val isAdmin = try {
            userPrefsRepo.isAdminRole()
        } catch (_: Exception) {
            false
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E2C))
                    .padding(14.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                if (!isAdmin) {
                    Text(
                        text = "🔒 Modo Admin Requerido",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color(0xFFFFB74D))
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = "Desbloquea el modo admin desde la app para usar este control",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(Color(0xFFE0E0E0))
                        )
                    )
                } else {
                    Text(
                        text = "🎛️ Control Rápido de Estado",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color(0xFFFFFFFF))
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                    ) {
                        Button(
                            text = "🚶 Salí / En camino",
                            onClick = actionRunCallback<ControlOnTheWayAction>()
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Button(
                            text = "🏠 Ya llegué",
                            onClick = actionRunCallback<ControlArrivedHomeAction>()
                        )
                    }
                }
            }
        }
    }
}
