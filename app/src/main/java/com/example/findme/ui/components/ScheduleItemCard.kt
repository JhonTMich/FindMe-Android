package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.IndigoPrimary
import com.example.findme.ui.theme.StatusGreen
import com.example.findme.ui.theme.StatusGreenBg
import com.example.findme.ui.theme.StatusGreenText
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary
import java.time.LocalTime
import java.time.ZonedDateTime

enum class ScheduleCardStatus {
    COMPLETADA,
    EN_CURSO,
    PROXIMA
}

@Composable
fun ScheduleItemCard(
    schedule: ScheduleDto,
    modifier: Modifier = Modifier,
    isActiveClass: Boolean = false,
    onCheckIn: () -> Unit = {},
    onDetails: () -> Unit = {}
) {
    val startFmt = schedule.startTime.take(5)
    val endFmt = schedule.endTime.take(5)

    val nowInLima = ZonedDateTime.now(StatusRepository.SYSTEM_ZONE).toLocalTime()
    val startTime = StatusRepository.parseLocalTimeOrNull(schedule.startTime) ?: LocalTime.MIN
    val endTime = StatusRepository.parseLocalTimeOrNull(schedule.endTime) ?: LocalTime.MAX

    val status = when {
        isActiveClass || (nowInLima.isAfter(startTime) && nowInLima.isBefore(endTime)) -> ScheduleCardStatus.EN_CURSO
        nowInLima.isAfter(endTime) -> ScheduleCardStatus.COMPLETADA
        else -> ScheduleCardStatus.PROXIMA
    }

    val durationText = calculateDurationText(startTime, endTime)

    val cardBg = if (status == ScheduleCardStatus.EN_CURSO) Color(0xFF161F38) else DarkCardBg
    val strokeColor = if (status == ScheduleCardStatus.EN_CURSO) IndigoPrimary else DarkCardBorder
    val stripeColor = when (status) {
        ScheduleCardStatus.COMPLETADA -> Color(0xFF6366F1)
        ScheduleCardStatus.EN_CURSO -> IndigoPrimary
        ScheduleCardStatus.PROXIMA -> Color(0xFF34D399)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (status == ScheduleCardStatus.EN_CURSO) 1.5.dp else 1.dp, strokeColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Left Indicator Stripe Line
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = if (status == ScheduleCardStatus.EN_CURSO) 180.dp else 90.dp)
                    .background(color = stripeColor, shape = CircleShape)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Header Row: Time + Duration + Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (status == ScheduleCardStatus.PROXIMA) Color(0xFF34D399) else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$startFmt — $endFmt",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status == ScheduleCardStatus.EN_CURSO) Color.White else TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "($durationText)",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    // Status Badge
                    when (status) {
                        ScheduleCardStatus.COMPLETADA -> {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = StatusGreenBg,
                                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = StatusGreenText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "COMPLETADA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusGreenText
                                    )
                                }
                            }
                        }
                        ScheduleCardStatus.EN_CURSO -> {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = StatusGreenBg,
                                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(StatusGreen, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "EN CURSO",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusGreenText
                                    )
                                }
                            }
                        }
                        ScheduleCardStatus.PROXIMA -> {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PRÓXIMA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Activity Title
                Text(
                    text = schedule.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Subtitle / Description if present
                val description = getSubtitleForSchedule(schedule)
                if (description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA5B4FC)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Info Rows depending on Status
                if (status == ScheduleCardStatus.EN_CURSO) {
                    // 2x2 Info Grid for Active Activity
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Ubicación", fontSize = 11.sp, color = TextMuted)
                                }
                                Text(
                                    text = schedule.location.ifEmpty { "Casa / Estudio" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Prioridad", fontSize = 11.sp, color = TextMuted)
                                }
                                Text(
                                    text = "Alta (Sprint)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Modalidad", fontSize = 11.sp, color = TextMuted)
                                }
                                Text(
                                    text = if (schedule.modality.isNotBlank()) schedule.modality else "Remoto / Foco",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons Row: Registrar Check-in + Detalles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCheckIn,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(2f)
                                .height(40.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Registrar Check-in",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onDetails,
                            border = BorderStroke(1.dp, Color(0xFF2E3B56)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF161F33)),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Detalles",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    // Standard Info Row for Completed or Upcoming Activity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Domain,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = schedule.location.ifEmpty { "Aula 302 • Pabellón Principal" },
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Prof. Roberto Morales",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun calculateDurationText(start: LocalTime, end: LocalTime): String {
    val startMinutes = start.hour * 60 + start.minute
    val endMinutes = end.hour * 60 + end.minute
    val diff = (endMinutes - startMinutes).coerceAtLeast(0)

    val hours = diff / 60
    val mins = diff % 60

    return if (hours > 0) {
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h 00m"
    } else {
        "${mins}m"
    }
}

private fun getSubtitleForSchedule(schedule: ScheduleDto): String {
    val clean = schedule.title.lowercase()
    return when {
        clean.contains("algoritmos") -> "Teoría avanzada: Grafos y Árboles B+"
        clean.contains("bases de datos") -> "Optimización de consultas SQL y Sharding"
        clean.contains("estudio") || clean.contains("frontend") -> "Implementación de componentes y diseño responsive mobile"
        clean.contains("cardio") || clean.contains("gimnasio") -> "Rutina de resistencia y pesas libres"
        else -> ""
    }
}
