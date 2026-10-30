package com.example.findme.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.findme.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.findme.data.model.ScheduleDto
import com.example.findme.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale

@Composable
fun WeeklyScheduleHeader(allSchedules: List<ScheduleDto>, selectedDayOfWeek: Int,
    onDaySelected: (Int) -> Unit, showDayTitle: Boolean = true) {
    var chartVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chartVisible = true }
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tu semana", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es")))} — ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es")))}",
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Card(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, DarkCardBorder),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1E2443), DarkCardBg))).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PANORAMA SEMANAL", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = TextSecondary)
                        Text("${allSchedules.size} ${if (allSchedules.size == 1) "actividad" else "actividades"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    val activeDays = allSchedules.map { it.dayOfWeek }.distinct().size
                    StatusPill("$activeDays ${if (activeDays == 1) "día" else "días"}", Color(0xFF67D8E8))
                }
                val maximum = (1..7).maxOf { day -> allSchedules.count { it.dayOfWeek == day } }.coerceAtLeast(1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    (1..7).forEach { day ->
                        val count = allSchedules.count { it.dayOfWeek == day }
                        val height by animateFloatAsState(if (chartVisible) count.toFloat() / maximum else 0f, tween(700), label = "weekBar$day")
                        val color by animateColorAsState(if (selectedDayOfWeek == day) MaterialTheme.colorScheme.primary else Color(0xFF4A607C), tween(250), label = "weekBarColor$day")
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$count", style = MaterialTheme.typography.labelSmall, color = if (selectedDayOfWeek == day) MaterialTheme.colorScheme.primary else TextMuted)
                            Box(Modifier.height(62.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().height((5 + 57 * height).dp).clip(RoundedCornerShape(5.dp))
                                    .background(Brush.verticalGradient(listOf(color, color.copy(alpha = .3f)))))
                            }
                            Text(listOf("L", "M", "X", "J", "V", "S", "D")[day - 1], color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { day ->
                val date = monday.plusDays((day - 1).toLong())
                val dayColor by animateColorAsState(if (selectedDayOfWeek == day) MaterialTheme.colorScheme.primaryContainer else DarkCardBg, tween(250), label = "dayColor$day")
                Surface(onClick = { onDaySelected(day) }, modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = dayColor,
                    contentColor = if (selectedDayOfWeek == day) MaterialTheme.colorScheme.primary else TextSecondary,
                    border = androidx.compose.foundation.BorderStroke(1.dp,
                        if (selectedDayOfWeek == day) MaterialTheme.colorScheme.primary else com.example.findme.ui.theme.DarkCardBorder)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("es"))).take(2),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        val selectedCount = allSchedules.count { it.dayOfWeek == selectedDayOfWeek }
        Text("$selectedCount ${if (selectedCount == 1) "actividad programada" else "actividades programadas"}",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        if (showDayTitle) Text(DayOfWeek.of(selectedDayOfWeek).getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("es"))
            .replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
    }
}
