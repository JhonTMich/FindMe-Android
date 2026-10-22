package com.example.findme.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ActivityCategory(
    val key: String,
    val label: String,
    val accentColor: Color,
    val containerColor: Color,
    val badgeBgColor: Color,
    val icon: ImageVector
) {
    UNIVERSITY(
        key = "university",
        label = "Universidad",
        accentColor = Color(0xFFE55B44),
        containerColor = Color(0xFFFDF0ED),
        badgeBgColor = Color(0xFFA05244),
        icon = Icons.Default.School
    ),
    WORK(
        key = "work",
        label = "Trabajo",
        accentColor = Color(0xFF00897B),
        containerColor = Color(0xFFE0F2F1),
        badgeBgColor = Color(0xFF004D40),
        icon = Icons.Default.Work
    ),
    FREE(
        key = "free",
        label = "Tiempo Libre",
        accentColor = Color(0xFF2E7D32),
        containerColor = Color(0xFFE8F5E9),
        badgeBgColor = Color(0xFF1B5E20),
        icon = Icons.Default.Home
    );

    companion object {
        fun fromKey(key: String?, title: String = ""): ActivityCategory {
            val keyClean = key?.lowercase()?.trim().orEmpty()
            val titleClean = title.lowercase().trim()

            return when {
                keyClean == "work" || titleClean.contains("trabajo") || titleClean.contains("oficina") -> WORK
                keyClean == "free" || keyClean == "meal" || titleClean.contains("libre") || titleClean.contains("almuerzo") || titleClean.contains("gimnasio") -> FREE
                else -> UNIVERSITY
            }
        }
    }
}
