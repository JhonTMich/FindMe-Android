package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
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
import com.example.findme.ui.theme.StatusGreen
import com.example.findme.ui.theme.StatusGreenBg
import com.example.findme.ui.theme.StatusGreenText
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

@Composable
fun FindMeTopHeader(
    motherName: String = "",
    modifier: Modifier = Modifier,
    isSyncing: Boolean = false,
    onLogout: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        // Top Row: Logo & Profile Avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Logo & Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF262758),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = Color(0xFF93C5FD),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "FindMe",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // User Profile Avatar with Online Status Dot
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.clickable { onLogout() }
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E283D),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = motherName,
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Green indicator dot
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(StatusGreen, CircleShape)
                        .border(2.dp, Color(0xFF090D16), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Subtitle
        Text(
            text = "Monitoreo en tiempo real",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Screen Title Row: "Hoy" + "Sincronizado" Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hoy",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Pill chip badge "Sincronizado"
            Surface(
                shape = RoundedCornerShape(50),
                color = StatusGreenBg,
                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(StatusGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSyncing) "Sincronizando..." else "Sincronizado",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreenText
                    )
                }
            }
        }
    }
}


