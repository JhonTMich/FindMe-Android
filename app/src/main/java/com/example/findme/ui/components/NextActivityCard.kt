package com.example.findme.ui.components

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
import androidx.compose.material.icons.filled.NearMe
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
import com.example.findme.data.model.ActivityCategory
import com.example.findme.data.model.ScheduleDto

private val DarkTeal = Color(0xFF005F56)
private val CardBg = Color(0xFFFFFFFF)
private val InnerContainerBg = Color(0xFFF2F7FA)

@Composable
fun NextActivityCard(
    nextSchedule: ScheduleDto?,
    followingSchedule: ScheduleDto?,
    minutesUntilNext: Long?,
    modifier: Modifier = Modifier
) {
    if (nextSchedule == null) return

    val category = ActivityCategory.fromKey(nextSchedule.category, nextSchedule.title)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color(0xFFE8ECEF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⏩ ",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "PRÓXIMA ACTIVIDAD • A CONTINUACIÓN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTeal,
                        letterSpacing = 0.5.sp
                    )
                }

                if (minutesUntilNext != null && minutesUntilNext > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE0F2F1)
                    ) {
                        Text(
                            text = "En $minutesUntilNext min",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTeal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inner Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = InnerContainerBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Icon Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = category.containerColor,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = category.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = nextSchedule.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = category.containerColor
                            ) {
                                Text(
                                    text = category.label.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = category.accentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF616161),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${nextSchedule.startTime.take(5)} - ${nextSchedule.endTime.take(5)}",
                                fontSize = 12.sp,
                                color = Color(0xFF616161)
                            )

                            Text(text = " • ", fontSize = 12.sp, color = Color.Gray)

                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = Color(0xFF616161),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = nextSchedule.location,
                                fontSize = 12.sp,
                                color = Color(0xFF616161)
                            )
                        }
                    }
                }
            }

            // Footer row ("Luego:")
            if (followingSchedule != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Luego: ",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = followingSchedule.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF37474F)
                        )
                    }

                    Text(
                        text = "${followingSchedule.startTime.take(5)} - ${followingSchedule.endTime.take(5)}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
