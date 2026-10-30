package com.example.findme.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

@Composable
fun ActiveActivityCard(
    currentSchedule: ScheduleDto? = null,
    onCheckIn: () -> Unit = {},
    onAdjust: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val title = currentSchedule?.title ?: "Estudio y proyectos de desarrollo"
    val startTime = currentSchedule?.startTime?.take(5) ?: "15:00"
    val endTime = currentSchedule?.endTime?.take(5) ?: "18:30"
    val location = currentSchedule?.location?.ifEmpty { "Casa / Estudio" } ?: "Casa / Estudio"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Icon + ACTIVIDAD EN CURSO + Duration Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF22264C),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "ACTIVIDAD EN CURSO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8),
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Duration Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B243B)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "3h",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD)
                        )
                        Text(
                            text = "30m",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = Color(0xFF1E283D), thickness = 1.dp)

            Spacer(modifier = Modifier.height(14.dp))

            // Details 3-Columns Row: Horario, Ubicación, Prioridad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Column 1: Horario
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Horario",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "$startTime – $endTime",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Column 2: Ubicación
                Column(modifier = Modifier.weight(1.1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ubicación",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = location,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }

                // Column 3: Prioridad
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Prioridad",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Alta (Examen)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Buttons Row: Registrar Check-in + Ajustar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Check-in Button
                Button(
                    onClick = onCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(2f)
                        .height(44.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Registrar Check-in",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Adjust Button
                OutlinedButton(
                    onClick = onAdjust,
                    border = BorderStroke(1.dp, Color(0xFF2E3B56)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF161F33)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ajustar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
