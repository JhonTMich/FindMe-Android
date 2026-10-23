package com.example.findme.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.ui.theme.AccentFree
import com.example.findme.ui.theme.AccentUniversity
import com.example.findme.ui.theme.AccentWork
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.DarkInnerBg
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

@Composable
fun HeroStatusCard(
    status: UserCurrentStatus,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    when (status) {
        is UserCurrentStatus.InClass -> {
            InClassHeroCard(
                status = status,
                alphaPulse = alphaPulse,
                modifier = modifier
            )
        }
        is UserCurrentStatus.Override -> {
            OverrideHeroCard(
                status = status,
                alphaPulse = alphaPulse,
                modifier = modifier
            )
        }
        is UserCurrentStatus.FreeTime -> {
            FreeTimeHeroCard(
                status = status,
                alphaPulse = alphaPulse,
                modifier = modifier
            )
        }
        is UserCurrentStatus.Loading -> {
            LoadingHeroCard(modifier = modifier)
        }
        is UserCurrentStatus.Error -> {
            ErrorHeroCard(message = status.message, modifier = modifier)
        }
    }
}

@Composable
private fun InClassHeroCard(
    status: UserCurrentStatus.InClass,
    alphaPulse: Float,
    modifier: Modifier = Modifier
) {
    val startFmt = formatTimeAmPm(status.currentSchedule.startTime)
    val endFmt = formatTimeAmPm(status.currentSchedule.endTime)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            // Header Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Dot + Status
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentUniversity.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(alphaPulse)
                                .background(AccentUniversity, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EN VIVO AHORA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentUniversity,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Category Badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentUniversity.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = AccentUniversity,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Universidad",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentUniversity
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Title
            Text(
                text = "${status.displayName} está en la Universidad",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Course Subtitle
            Text(
                text = "Clase de ${status.currentSchedule.title}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Location Chip
            Surface(
                shape = RoundedCornerShape(50),
                color = DarkInnerBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AccentWork,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = status.currentSchedule.location,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Progress Section Box
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkInnerBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Top Time Range + Remaining Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = AccentUniversity,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$startFmt - $endFmt",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AccentUniversity
                        ) {
                            Text(
                                text = "Termina en ${status.remainingMinutes} min",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = AccentUniversity,
                        trackColor = AccentUniversity.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Row Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inicio: $startFmt",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Text(
                            text = "${(status.progress * 100).toInt()}% completado",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentUniversity
                        )

                        Text(
                            text = "Fin: $endFmt",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverrideHeroCard(
    status: UserCurrentStatus.Override,
    alphaPulse: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentWork.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(alphaPulse)
                                .background(AccentWork, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EN VIVO AHORA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentWork,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentWork.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentWork,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Aviso Especial",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentWork
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "${status.displayName} está en:",
                fontSize = 15.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status.customLocation,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (!status.customMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkInnerBg
                ) {
                    Text(
                        text = "💬 \"${status.customMessage}\"",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Dual Status Badge: Virtual Class while on the way
            if (status.concurrentVirtualClass != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentUniversity.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, AccentUniversity.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎧",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "En clase virtual: ${status.concurrentVirtualClass.title}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentUniversity
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeTimeHeroCard(
    status: UserCurrentStatus.FreeTime,
    alphaPulse: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentFree.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(alphaPulse)
                                .background(AccentFree, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EN VIVO AHORA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentFree,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentFree.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = AccentFree,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tiempo Libre",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentFree
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "${status.displayName} está en Casa",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AccentFree
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Sin clases programadas en este momento 🏠",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun LoadingHeroCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Consultando estado...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ErrorHeroCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Text(
                text = "Error al obtener estado",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

private fun formatTimeAmPm(timeStr: String): String {
    return try {
        val parts = timeStr.trim().split(":")
        if (parts.size >= 2) {
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val amPm = if (hour >= 12) "PM" else "AM"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format("%02d:%02d %s", hour12, minute, amPm)
        } else timeStr
    } catch (_: Exception) {
        timeStr
    }
}
