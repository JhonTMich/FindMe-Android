package com.example.findme.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.data.model.ActivityCategory
import com.example.findme.ui.theme.*

fun formatDurationMinutes(minutes: Long): String {
    val value = minutes.coerceAtLeast(0)
    return if (value >= 60) "${value / 60} h ${value % 60} min" else "$value min"
}

@Composable
fun HeroStatusCard(status: UserCurrentStatus, modifier: Modifier = Modifier) {
    val accent = when (status) {
        is UserCurrentStatus.InClass -> ActivityCategory.fromKey(status.currentSchedule.category, status.currentSchedule.title).accentColor
        is UserCurrentStatus.Override -> BrandCyan
        else -> StatusGreenText
    }
    Card(modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(DarkSurface, Color(0xFF23254F), DarkCardBg)))) {
            BrandOrbit(Modifier.alpha(.14f).align(Alignment.TopEnd).offset(x = 94.dp, y = (-44).dp).size(280.dp))
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrbitAvatar(status.displayName)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("ESTADO ACTUAL", style = MaterialTheme.typography.labelSmall, color = TextSecondary, letterSpacing = 1.5.sp)
                        Text(status.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
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
                Text(title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp, color = Color.White)
                Text(when (status) {
                    is UserCurrentStatus.InClass -> listOf(status.currentSchedule.location,
                        "Termina en ${formatDurationMinutes(status.remainingMinutes)}").filter { it.isNotBlank() }.joinToString(" · ")
                    is UserCurrentStatus.Override -> status.customMessage ?: "Compartido por ${status.displayName}"
                    is UserCurrentStatus.Loading -> "Preparando la información de hoy"
                    is UserCurrentStatus.Error -> status.message
                    else -> "Según el horario de hoy. La ubicación no está confirmada."
                }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (status is UserCurrentStatus.InClass) {
                    val progress by animateFloatAsState(status.progress.coerceIn(0f, 1f), tween(800), label = "activityProgress")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = accent, trackColor = Color.White.copy(alpha = .1f))
                }
                HorizontalDivider(color = Color.White.copy(alpha = .1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (status is UserCurrentStatus.Override) Icons.Default.Forum else Icons.Default.CalendarToday, null,
                        tint = accent, modifier = Modifier.size(15.dp))
                    Text(if (status is UserCurrentStatus.Override) "Información compartida" else "Información del horario de hoy",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}
