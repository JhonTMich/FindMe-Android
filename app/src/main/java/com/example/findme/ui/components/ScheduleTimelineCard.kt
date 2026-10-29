package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.DarkInnerBg
import com.example.findme.ui.theme.IndigoCardBg
import com.example.findme.ui.theme.IndigoCardBorder
import com.example.findme.ui.theme.IndigoPrimary
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary
import java.time.LocalTime
import java.time.ZonedDateTime

enum class ScheduleItemStatusState {
    COMPLETED,
    EN_CURSO,
    PENDIENTE
}

data class TimelineScheduleItem(
    val id: String,
    val startTime: String,
    val endTime: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val statusState: ScheduleItemStatusState,
    val isLocationConfirmed: Boolean = true,
    val remainingText: String = ""
)

@Composable
fun ScheduleTimelineCard(
    schedules: List<ScheduleDto>,
    modifier: Modifier = Modifier
) {
    val items = if (schedules.isNotEmpty()) {
        val nowInLima = ZonedDateTime.now(StatusRepository.SYSTEM_ZONE).toLocalTime()
        schedules.map { dto ->
            val start = StatusRepository.parseLocalTimeOrNull(dto.startTime) ?: LocalTime.MIN
            val end = StatusRepository.parseLocalTimeOrNull(dto.endTime) ?: LocalTime.MAX

            val statusState = when {
                nowInLima.isAfter(end) -> ScheduleItemStatusState.COMPLETED
                nowInLima.isBefore(start) -> ScheduleItemStatusState.PENDIENTE
                else -> ScheduleItemStatusState.EN_CURSO
            }

            val icon = getIconForTitle(dto.title)
            val remainingMins = if (statusState == ScheduleItemStatusState.EN_CURSO) {
                val currentMinutes = nowInLima.hour * 60 + nowInLima.minute
                val endMinutes = end.hour * 60 + end.minute
                (endMinutes - currentMinutes).coerceAtLeast(0)
            } else 0

            TimelineScheduleItem(
                id = dto.id.ifEmpty { dto.title },
                startTime = dto.startTime.take(5),
                endTime = dto.endTime.take(5),
                title = dto.title,
                subtitle = dto.location.ifEmpty { "Ubicación confirmada" },
                icon = icon,
                statusState = statusState,
                isLocationConfirmed = true,
                remainingText = if (remainingMins > 0) "Restan $remainingMins min" else ""
            )
        }
    } else {
        // Mock items matching design screenshot when no DB data exists
        listOf(
            TimelineScheduleItem(
                id = "1",
                startTime = "08:00",
                endTime = "09:30",
                title = "Desayuno y preparación",
                subtitle = "Casa • Rutina matutina",
                icon = Icons.Default.Restaurant,
                statusState = ScheduleItemStatusState.COMPLETED
            ),
            TimelineScheduleItem(
                id = "2",
                startTime = "10:00",
                endTime = "13:00",
                title = "Clases teóricas",
                subtitle = "Campus Central • Edificio B Aula 204",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                statusState = ScheduleItemStatusState.COMPLETED
            ),
            TimelineScheduleItem(
                id = "3",
                startTime = "13:30",
                endTime = "14:30",
                title = "Almuerzo en cafetería",
                subtitle = "Cafetería Universitaria",
                icon = Icons.Default.LocalCafe,
                statusState = ScheduleItemStatusState.COMPLETED
            ),
            TimelineScheduleItem(
                id = "4",
                startTime = "15:00",
                endTime = "18:30",
                title = "Estudio y proyectos – Casa",
                subtitle = "Ubicación confirmada",
                icon = Icons.Default.Laptop,
                statusState = ScheduleItemStatusState.EN_CURSO,
                remainingText = "Restan 45 min"
            ),
            TimelineScheduleItem(
                id = "5",
                startTime = "19:30",
                endTime = "20:30",
                title = "Gimnasio o descanso",
                subtitle = "Club Deportivo Norte",
                icon = Icons.Default.FitnessCenter,
                statusState = ScheduleItemStatusState.PENDIENTE
            ),
            TimelineScheduleItem(
                id = "6",
                startTime = "21:00",
                endTime = "22:00",
                title = "Tiempo libre y descanso",
                subtitle = "Casa",
                icon = Icons.Default.Schedule,
                statusState = ScheduleItemStatusState.PENDIENTE
            )
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isLast = index == items.size - 1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    // Left Timeline Column (Node + Vertical Line)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(32.dp)
                    ) {
                        // Timeline Node Dot
                        when (item.statusState) {
                            ScheduleItemStatusState.COMPLETED -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(2.dp, Color(0xFF334155), CircleShape)
                                            .background(Color(0xFF0F172A), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF94A3B8), CircleShape)
                                        )
                                    }
                                }
                            }
                            ScheduleItemStatusState.EN_CURSO -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .border(2.dp, IndigoPrimary, CircleShape)
                                            .background(Color(0xFF1E1B4B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(IndigoPrimary, CircleShape)
                                        )
                                    }
                                }
                            }
                            ScheduleItemStatusState.PENDIENTE -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(2.dp, Color(0xFF334155), CircleShape)
                                            .background(Color(0xFF0F172A), CircleShape)
                                    )
                                }
                            }
                        }

                        // Connecting Vertical Line
                        if (!isLast) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF1E293B))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Right Item Content Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (isLast) 0.dp else 14.dp)
                    ) {
                        TimelineItemContentCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItemContentCard(item: TimelineScheduleItem) {
    val isEnCurso = item.statusState == ScheduleItemStatusState.EN_CURSO

    val cardBg = if (isEnCurso) IndigoCardBg else DarkInnerBg
    val cardBorder = if (isEnCurso) IndigoCardBorder else Color(0xFF1E293B)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Time Range + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.startTime} – ${item.endTime}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnCurso) Color.White else TextMuted
                )

                when (item.statusState) {
                    ScheduleItemStatusState.COMPLETED -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = "Completado",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    ScheduleItemStatusState.EN_CURSO -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = IndigoPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "En curso",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    ScheduleItemStatusState.PENDIENTE -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = "Pendiente",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Title Row: Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isEnCurso) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF3730A3),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.statusState == ScheduleItemStatusState.COMPLETED) TextMuted else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = if (isEnCurso) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (item.statusState == ScheduleItemStatusState.COMPLETED) TextSecondary else TextPrimary,
                    textDecoration = if (item.statusState == ScheduleItemStatusState.COMPLETED) TextDecoration.LineThrough else TextDecoration.None
                )
            }

            // Subtitle or Footer Row
            if (isEnCurso) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ubicación confirmada",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }

                    if (item.remainingText.isNotEmpty()) {
                        Text(
                            text = item.remainingText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA5B4FC)
                        )
                    }
                }
            } else if (item.subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
}

private fun getIconForTitle(title: String): ImageVector {
    val clean = title.lowercase()
    return when {
        clean.contains("desayuno") || clean.contains("almuerzo") || clean.contains("comida") -> Icons.Default.Restaurant
        clean.contains("cafetería") || clean.contains("café") -> Icons.Default.LocalCafe
        clean.contains("clase") || clean.contains("teórica") || clean.contains("universidad") -> Icons.AutoMirrored.Filled.MenuBook
        clean.contains("estudio") || clean.contains("proyecto") || clean.contains("laptop") -> Icons.Default.Laptop
        clean.contains("gimnasio") || clean.contains("deporte") || clean.contains("entreno") -> Icons.Default.FitnessCenter
        else -> Icons.Default.Schedule
    }
}
