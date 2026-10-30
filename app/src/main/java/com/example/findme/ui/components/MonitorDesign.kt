package com.example.findme.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.model.ActivityCategory
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.ui.theme.*
import java.time.Duration
import java.time.LocalTime

@Composable
fun RevealCard(index: Int = 0, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f,
        tween(450, delayMillis = index.coerceAtMost(4) * 65, easing = FastOutSlowInEasing), label = "cardEntrance")
    Box(Modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * 28.dp.toPx() }) { content() }
}

@Composable
fun AccentIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun StatusPill(text: String, color: Color = MaterialTheme.colorScheme.primary, pulse: Boolean = false) {
    Surface(color = color.copy(alpha = .13f), shape = CircleShape, border = BorderStroke(1.dp, color.copy(alpha = .25f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pulse) {
                val transition = rememberInfiniteTransition(label = "live")
                val opacity by transition.animateFloat(.4f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "liveDot")
                Box(Modifier.size(6.dp).graphicsLayer { alpha = opacity }.background(color, CircleShape))
            }
            Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DayOverview(schedules: List<ScheduleDto>) {
    val now = LocalTime.now()
    val finished = schedules.count { StatusRepository.parseLocalTimeOrNull(it.endTime)?.let { end -> !now.isBefore(end) } == true }
    val minutes = schedules.sumOf {
        val start = StatusRepository.parseLocalTimeOrNull(it.startTime)
        val end = StatusRepository.parseLocalTimeOrNull(it.endTime)
        if (start != null && end != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
    }
    val fraction by animateFloatAsState(if (schedules.isEmpty()) 0f else finished.toFloat() / schedules.size, tween(800), label = "dayProgress")
    Card(colors = CardDefaults.cardColors(containerColor = DarkCardBg), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, DarkCardBorder)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.DonutLarge, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                Text("El día de un vistazo", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("${(fraction * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewMetric("${schedules.size}", "Actividades", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                OverviewMetric(if (minutes >= 60) "${minutes / 60}h" + if (minutes % 60 > 0) " ${minutes % 60}m" else "" else "${minutes}m", "Programadas", Color(0xFF67D8E8), Modifier.weight(1f))
                OverviewMetric("$finished", "Finalizadas", StatusGreenText, Modifier.weight(1f))
            }
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
        }
    }
}

@Composable
private fun OverviewMetric(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ActivityTimelineCard(schedule: ScheduleDto, active: Boolean, last: Boolean = false) {
    val category = ActivityCategory.fromKey(schedule.category, schedule.title)
    val accent = category.accentColor
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(46.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(schedule.startTime.take(5), style = MaterialTheme.typography.labelMedium, color = if (active) accent else TextSecondary)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(10.dp).background(accent.copy(alpha = .15f), CircleShape).padding(2.dp).background(accent, CircleShape))
            if (!last) Box(Modifier.padding(top = 7.dp).width(1.dp).weight(1f).background(DarkCardBorder))
        }
        Card(Modifier.weight(1f).padding(bottom = 4.dp).animateContentSize(), shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, if (active) accent.copy(alpha = .6f) else DarkCardBorder)) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.copy(alpha = if (active) .18f else .08f), DarkCardBg)))
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(category.icon, null, tint = accent, modifier = Modifier.size(16.dp))
                    Text(category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.weight(1f))
                    if (active) StatusPill("En curso", StatusGreenText, pulse = true)
                }
                Text(schedule.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                if (schedule.location.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = TextMuted, modifier = Modifier.size(15.dp))
                    Text(schedule.location, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Text("Hasta ${schedule.endTime.take(5)}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    if (schedule.modality.isNotBlank()) Text("· ${schedule.modality}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ScheduleEmptyCard(loading: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, DarkCardBorder),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(76.dp).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .18f), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                else Icon(Icons.Default.EventAvailable, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
            Text(if (loading) "Cargando actividades…" else "Un espacio para desconectar", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(if (loading) "Preparando el horario" else "No hay actividades programadas para este día.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
