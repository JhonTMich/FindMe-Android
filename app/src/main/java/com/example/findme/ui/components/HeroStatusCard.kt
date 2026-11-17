package com.example.findme.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.data.model.ActivityCategory
import com.example.findme.ui.theme.*
import com.example.findme.R
import com.example.findme.widget.WidgetScene
import com.example.findme.widget.sceneForSharedStatus
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

/** Same illustrations as the home-screen widget, so the app and the widget tell the status the same way. */
private fun sceneArt(status: UserCurrentStatus): Int? {
    val scene = when (status) {
        is UserCurrentStatus.InClass -> when (ActivityCategory.fromKey(status.currentSchedule.category, status.currentSchedule.title)) {
            ActivityCategory.WORK -> WidgetScene.WORK
            ActivityCategory.UNIVERSITY -> WidgetScene.UNIVERSITY
            ActivityCategory.FREE -> WidgetScene.FREE
        }
        is UserCurrentStatus.Override -> sceneForSharedStatus("${status.customLocation} ${status.customMessage.orEmpty()}")
        is UserCurrentStatus.Loading, is UserCurrentStatus.Error -> return null
        else -> WidgetScene.FREE
    }
    return when (scene) {
        WidgetScene.WORK -> R.drawable.widget_art_work
        WidgetScene.UNIVERSITY -> R.drawable.widget_art_university
        WidgetScene.FREE -> R.drawable.widget_art_free
        WidgetScene.STREET -> R.drawable.widget_art_street
    }
}

fun formatDurationMinutes(minutes: Long): String {
    val value = minutes.coerceAtLeast(0)
    return if (value >= 60) "${value / 60} h ${value % 60} min" else "$value min"
}

@Composable
fun HeroStatusCard(status: UserCurrentStatus, modifier: Modifier = Modifier) {
    val accent = when (status) {
        is UserCurrentStatus.InClass -> ActivityCategory.fromKey(status.currentSchedule.category, status.currentSchedule.title).accentColor
        is UserCurrentStatus.Override -> BrandSky
        else -> StatusGreenText
    }
    Card(modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(HeroGradient))) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileAvatar(status.displayName)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("ESTADO ACTUAL", style = MaterialTheme.typography.labelSmall, color = TextSecondary, letterSpacing = 1.5.sp)
                        Text(status.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
                // Status on the left, the scene's illustration on the right (as in the widget).
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        StatusPill(when (status) {
                            is UserCurrentStatus.InClass -> "Actividad en curso"
                            is UserCurrentStatus.Override -> "Estado compartido"
                            is UserCurrentStatus.Loading -> "Consultando"
                            is UserCurrentStatus.Error -> "Sin información"
                            else -> "Según el horario"
                        }, accent, status is UserCurrentStatus.InClass || status is UserCurrentStatus.Override)
                        val title = when (status) {
                            is UserCurrentStatus.InClass -> status.currentSchedule.title
                            is UserCurrentStatus.Override -> status.customLocation
                            is UserCurrentStatus.Loading -> "Consultando actividad…"
                            is UserCurrentStatus.Error -> "Estado no disponible"
                            else -> "Sin actividad en curso"
                        }
                        Text(title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp, color = TextPrimary)
                        Text(when (status) {
                            is UserCurrentStatus.InClass -> listOf(status.currentSchedule.location,
                                "Termina en ${formatDurationMinutes(status.remainingMinutes)}").filter { it.isNotBlank() }.joinToString(" · ")
                            is UserCurrentStatus.Override -> status.customMessage ?: "Al terminar este estado, se mostrará el horario."
                            is UserCurrentStatus.Loading -> "Preparando la información de hoy"
                            is UserCurrentStatus.Error -> status.message
                            else -> "Según el horario de hoy. La ubicación no está confirmada."
                        }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    sceneArt(status)?.let { art ->
                        Image(painterResource(art), contentDescription = null, modifier = Modifier.width(118.dp).aspectRatio(574f / 614f))
                    }
                }
                if (status is UserCurrentStatus.InClass) {
                    val progress by animateFloatAsState(status.progress.coerceIn(0f, 1f), tween(800), label = "activityProgress")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = accent, trackColor = TextPrimary.copy(alpha = .12f))
                }
            }
        }
    }
}
