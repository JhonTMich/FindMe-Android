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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.model.ScheduleDto
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.IndigoPrimary
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CronogramaHoyCard(
    schedules: List<ScheduleDto>,
    onVerHorarioCompleto: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Calculate completion percentage dynamically or default to 65%
    val percentage = if (schedules.isNotEmpty()) {
        val completedCount = schedules.count { schedule ->
            // Assume completed if current time > endTime
            val now = java.time.LocalTime.now()
            val end = com.example.findme.data.repository.StatusRepository.parseLocalTimeOrNull(schedule.endTime)
            end != null && now.isAfter(end)
        }
        ((completedCount.toFloat() / schedules.size) * 100).toInt().coerceIn(0, 100)
    } else {
        65
    }

    // Format current date in Spanish, e.g. "Miércoles, 24 de Octubre"
    val dateText = try {
        val today = LocalDate.now()
        val spanishLocale = Locale.forLanguageTag("es-ES")
        val dayName = today.format(DateTimeFormatter.ofPattern("EEEE", spanishLocale)).capitalize(spanishLocale)
        val dayNum = today.dayOfMonth
        val monthName = today.format(DateTimeFormatter.ofPattern("MMMM", spanishLocale)).capitalize(spanishLocale)
        "$dayName, $dayNum de $monthName"
    } catch (_: Exception) {
        "Miércoles, 24 de Octubre"
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
                .padding(20.dp)
        ) {
            // Row 1: Title + Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cronograma de hoy",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "$percentage%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF818CF8)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Date + "Día completado"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )

                Text(
                    text = "Día completado",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentage / 100f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary)
                )
            }
        }
    }
}

private fun String.capitalize(locale: Locale): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
