package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.DarkSurface

data class NavTabItem(
    val id: String,
    val title: String,
    val icon: ImageVector
)

@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    selectedTabId: String = "hoy",
    onTabSelected: (String) -> Unit
) {
    val tabs = listOf(
        NavTabItem("hoy", "Hoy", Icons.Default.CalendarToday),
        NavTabItem("horario", "Horario", Icons.Default.CalendarMonth),
        NavTabItem("perfil", "Perfil", Icons.Default.PersonOutline)
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.id == selectedTabId

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(tab.id) }
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF22264C),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = Color(0xFFA5B4FC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tab.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF818CF8) else Color(0xFF64748B)
                    )
                }
            }
        }
    }
}
