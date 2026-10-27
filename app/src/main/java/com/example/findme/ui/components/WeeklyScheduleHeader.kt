package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.model.ScheduleDto
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.IndigoPrimary
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

data class DayPillInfo(
    val dayOfWeek: Int,
    val shortName: String,
    val dayNum: String,
    val count: Int
)

@Composable
fun WeeklyScheduleHeader(
    allSchedules: List<ScheduleDto> = emptyList(),
    selectedDayOfWeek: Int = 3,
    onDaySelected: (Int) -> Unit = {},
    isAgendaView: Boolean = true,
    onToggleViewMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Calculate activity counts per day (1..6)
    val countPerDay = (1..6).associateWith { day ->
        val list = allSchedules.filter { it.dayOfWeek == day }
        if (list.isNotEmpty()) list.size else when (day) {
            1 -> 3
            2 -> 3
            3 -> 4
            4 -> 4
            5 -> 3
            else -> 1
        }
    }

    val dayPills = listOf(
        DayPillInfo(1, "Lun", "23", countPerDay[1] ?: 3),
        DayPillInfo(2, "Mar", "24", countPerDay[2] ?: 3),
        DayPillInfo(3, "Mié", "25", countPerDay[3] ?: 4),
        DayPillInfo(4, "Jue", "26", countPerDay[4] ?: 4),
        DayPillInfo(5, "Vie", "27", countPerDay[5] ?: 3),
        DayPillInfo(6, "Sáb", "28", countPerDay[6] ?: 1)
    )

    val selectedDayPill = dayPills.find { it.dayOfWeek == selectedDayOfWeek } ?: dayPills[2]
    val selectedDayCount = selectedDayPill.count

    val maxCount = countPerDay.values.maxOrNull()?.coerceAtLeast(1) ?: 4

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. Top Row: Tag "Calendario de actividades" + "38 hrs programadas • 6 días"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF1E1B4B),
                border = BorderStroke(1.dp, Color(0xFF312E81))
            ) {
                Text(
                    text = "Calendario de actividades",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA5B4FC),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "38 hrs programadas • 6 días",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Title Row: "Horario Semanal" + Month Navigator "< Oct >"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Horario Semanal",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Semana 43 • 23 – 28 Octubre",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }

            // Month Navigator Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF161F33),
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Anterior",
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Oct",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Siguiente",
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. DISTRIBUCIÓN DE CARGA SEMANAL Bar Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DISTRIBUCIÓN DE CARGA SEMANAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = "${selectedDayPill.shortName}: $selectedDayCount actividades",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bar Chart Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dayPills.forEach { pill ->
                        val isSelected = pill.dayOfWeek == selectedDayOfWeek
                        val heightFraction = (pill.count.toFloat() / maxCount).coerceIn(0.25f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height((36 * heightFraction).dp)
                                    .background(
                                        color = if (isSelected) IndigoPrimary else Color(0xFF283454),
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                    )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = pill.shortName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Day Selector Pills Bar (Lun 23 3act, Mar 24 3act, Mié 25 4act, Jue 26 4act, Vie 27 3act, Sáb 28)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayPills.forEach { pill ->
                val isSelected = pill.dayOfWeek == selectedDayOfWeek

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) IndigoPrimary else Color(0xFF131A2E),
                    border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else Color(0xFF1E283D)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clickable { onDaySelected(pill.dayOfWeek) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = pill.shortName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White else TextMuted
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = pill.dayNum,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextPrimary
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) Color(0xFF3730A3) else Color(0xFF1E283D)
                        ) {
                            Text(
                                text = "${pill.count} act",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Category Legend Dots (Left) + View Mode Switcher (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Legend Dots
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF818CF8), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clases", fontSize = 12.sp, color = TextSecondary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFFA5B4FC), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Estudio", fontSize = 12.sp, color = TextSecondary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Gimnasio", fontSize = 12.sp, color = TextSecondary)
                }
            }

            // View Mode Switcher (Agenda vs Desglose)
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF131A2E),
                border = BorderStroke(1.dp, Color(0xFF1E283D))
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Agenda Button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isAgendaView) IndigoPrimary else Color.Transparent,
                        modifier = Modifier.clickable { onToggleViewMode(true) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (isAgendaView) Color.White else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Agenda",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAgendaView) Color.White else TextMuted
                            )
                        }
                    }

                    // Desglose Button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (!isAgendaView) IndigoPrimary else Color.Transparent,
                        modifier = Modifier.clickable { onToggleViewMode(false) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                tint = if (!isAgendaView) Color.White else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Desglose",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isAgendaView) Color.White else TextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Selected Day Title Header (Miércoles, 25 de Octubre + Hoy + 7.5 horas totales)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dayTitle = when (selectedDayOfWeek) {
                    1 -> "Lunes, 23 de Octubre"
                    2 -> "Martes, 24 de Octubre"
                    3 -> "Miércoles, 25 de Octubre"
                    4 -> "Jueves, 26 de Octubre"
                    5 -> "Viernes, 27 de Octubre"
                    6 -> "Sábado, 28 de Octubre"
                    else -> "Domingo, 29 de Octubre"
                }

                Text(
                    text = dayTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (selectedDayOfWeek == 3) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF1E1B4B),
                        border = BorderStroke(1.dp, Color(0xFF312E81))
                    ) {
                        Text(
                            text = "Hoy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA5B4FC),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = "7.5 horas totales",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }
    }
}
