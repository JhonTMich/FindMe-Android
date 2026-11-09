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
import com.example.findme.data.model.ActivityCategory
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.formatScheduleTime
import com.example.findme.domain.model.continuesOn
import com.example.findme.ui.theme.*

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
fun ActivityTimelineCard(schedule: ScheduleDto, active: Boolean, last: Boolean = false, displayDay: Int = schedule.dayOfWeek) {
    val category = ActivityCategory.fromKey(schedule.category, schedule.title)
    val accent = category.accentColor
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(68.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (schedule.continuesOn(displayDay)) "Desde ayer" else formatScheduleTime(schedule.startTime),
                style = MaterialTheme.typography.labelMedium, color = if (active) accent else TextSecondary)
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
                    val nextDay = if (schedule.endsNextDay && !schedule.continuesOn(displayDay)) " del día siguiente" else ""
                    Text("Hasta ${formatScheduleTime(schedule.endTime)}$nextDay", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    if (schedule.modality.isNotBlank()) Text("· ${schedule.modality}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
