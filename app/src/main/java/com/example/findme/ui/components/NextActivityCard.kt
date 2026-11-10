package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.formatScheduleTime
import com.example.findme.ui.theme.*

@Composable
fun NextActivityCard(next: ScheduleDto?, onOpenSchedule: () -> Unit) {
    Surface(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = DarkCardBg, border = BorderStroke(1.dp, DarkCardBorder)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AccentIcon(if (next == null) Icons.Default.CheckCircleOutline else Icons.Default.Schedule, BrandAccent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (next == null) "Nada más por hoy" else "Próxima en tu horario",
                    style = if (next == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                    color = if (next == null) TextPrimary else TextSecondary)
                if (next == null) {
                    Text("No tienes más actividades programadas para hoy.",
                        style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                } else {
                    Text(next.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                        fontWeight = FontWeight.SemiBold)
                    val tomorrow = if (next.endsNextDay || next.endTime.trim().startsWith("24:00")) " · termina mañana" else ""
                    Text("${formatScheduleTime(next.startTime)} – ${formatScheduleTime(next.endTime)}$tomorrow",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (next.location.isNotBlank()) Text(next.location, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text("Ver horario", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
