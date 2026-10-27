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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.DarkInnerBg
import com.example.findme.ui.theme.IndigoPrimary
import com.example.findme.ui.theme.StatusGreen
import com.example.findme.ui.theme.StatusGreenBg
import com.example.findme.ui.theme.StatusGreenText
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

fun formatDurationMinutes(minutes: Long): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    } else {
        "${mins}m"
    }
}

@Composable
fun HeroStatusCard(
    status: UserCurrentStatus,
    modifier: Modifier = Modifier,
    onSelectPoint: (String) -> Unit = {}
) {
    var selectedPoint by remember { mutableStateOf("Casa") }

    // Dynamic strings based on actual status
    val userName = if (status.displayName.isNotBlank() && status.displayName != "Usuario") {
        status.displayName
    } else {
        "Michael"
    }

    val (locationTitle, locationStatus, locationAddress) = when (status) {
        is UserCurrentStatus.InClass -> {
            Triple(
                status.currentSchedule.title,
                "En curso",
                status.currentSchedule.location.ifEmpty { "Campus Central • Edificio B" }
            )
        }
        is UserCurrentStatus.Override -> {
            Triple(
                status.customLocation,
                "En tránsito",
                "En camino a su destino"
            )
        }
        else -> {
            Triple(
                "En Casa",
                "Seguro",
                "Av. Los Olivos 420 • Zona Residencial"
            )
        }
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
                .padding(18.dp)
        ) {
            // 1. User Info Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // User Avatar Circle with Green Check Badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E283D),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Green Checkmark Badge at Bottom Right
                    Surface(
                        shape = CircleShape,
                        color = StatusGreen,
                        border = BorderStroke(2.dp, DarkCardBg),
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    // Name + Verified Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Last Updated Subtitle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Actualizado hace 10 min",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Middle Location Status Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkInnerBg,
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // House Icon Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF22254B),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = locationTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Badge "Seguro" / "En curso"
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = StatusGreenBg,
                                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = locationStatus,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreenText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = locationAddress,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Directions Action Icon
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1B243B),
                        border = BorderStroke(1.dp, Color(0xFF2E3B56)),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TurnRight,
                                contentDescription = "Dirección",
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Puntos de Frecuencia Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PUNTOS DE FRECUENCIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Radio activo",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF818CF8)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Pill filter buttons: Casa, Universidad, Calle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Casa
                val isCasa = selectedPoint == "Casa"
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isCasa) IndigoPrimary else Color(0xFF161F33),
                    border = BorderStroke(1.dp, if (isCasa) IndigoPrimary else Color(0xFF26334D)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedPoint = "Casa"
                            onSelectPoint("Casa")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Casa",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isCasa) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFA5B4FC), CircleShape)
                            )
                        }
                    }
                }

                // Button 2: Universidad
                val isUni = selectedPoint == "Universidad"
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isUni) IndigoPrimary else Color(0xFF161F33),
                    border = BorderStroke(1.dp, if (isUni) IndigoPrimary else Color(0xFF26334D)),
                    modifier = Modifier
                        .weight(1.2f)
                        .clickable {
                            selectedPoint = "Universidad"
                            onSelectPoint("Universidad")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Universidad",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }

                // Button 3: Calle
                val isCalle = selectedPoint == "Calle"
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isCalle) IndigoPrimary else Color(0xFF161F33),
                    border = BorderStroke(1.dp, if (isCalle) IndigoPrimary else Color(0xFF26334D)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedPoint = "Calle"
                            onSelectPoint("Calle")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Commute,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Calle",
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
