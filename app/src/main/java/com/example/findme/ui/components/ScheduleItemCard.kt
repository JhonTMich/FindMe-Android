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
import androidx.compose.material.icons.filled.LocationOn
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

@Composable
fun ScheduleItemCard(
    schedule: ScheduleDto,
    modifier: Modifier = Modifier,
    isActiveClass: Boolean = false
) {
    val containerBg = if (isActiveClass) Color(0xFFFFF3E0) else Color(0xFFFAFAFA)
    val strokeColor = if (isActiveClass) Color(0xFFFF7043) else Color(0xFFE0E0E0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(1.dp, strokeColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicator Stripe/Dot
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 48.dp)
                    .background(
                        color = if (isActiveClass) Color(0xFFFF7043) else Color(0xFFB0BEC5),
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val startFmt = schedule.startTime.take(5)
                    val endFmt = schedule.endTime.take(5)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActiveClass) Color(0xFFFFCCBC) else Color(0xFFECEFF1)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isActiveClass) Color(0xFFD84315) else Color(0xFF546E7A),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$startFmt - $endFmt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActiveClass) Color(0xFFD84315) else Color(0xFF37474F)
                            )
                        }
                    }

                    if (isActiveClass) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFF7043)
                        ) {
                            Text(
                                text = "EN CURSO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = schedule.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = schedule.location,
                        fontSize = 13.sp,
                        color = Color(0xFF616161)
                    )
                }
            }
        }
    }
}
