package com.example.findme.ui.components

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.DarkSurface

@Composable
fun BottomNavBar(modifier: Modifier = Modifier, selectedTabId: String = "hoy", onTabSelected: (String) -> Unit) {
    NavigationBar(modifier = modifier, containerColor = DarkSurface) {
        listOf(Triple("hoy", "Hoy", Icons.Default.Today),
            Triple("horario", "Horario", Icons.Default.CalendarMonth),
            Triple("perfil", "Perfil", Icons.Default.PersonOutline)).forEach { (id, title, icon) ->
            val scale by animateFloatAsState(if (selectedTabId == id) 1.12f else 1f, spring(dampingRatio = .65f), label = "navScale$id")
            NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted), selected = selectedTabId == id, onClick = { onTabSelected(id) },
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) }, label = { Text(title) })
        }
    }
}
