package com.example.findme.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.findme.ui.theme.AccentFree
import com.example.findme.ui.theme.AccentUniversity
import com.example.findme.ui.theme.AccentWork

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
        accentColor = AccentUniversity,
        containerColor = Color(0xFF2E1E1C),
        badgeBgColor = AccentUniversity,
        icon = Icons.Default.School
    ),
    WORK(
        key = "work",
        label = "Trabajo",
        accentColor = AccentWork,
        containerColor = Color(0xFF152A38),
        badgeBgColor = AccentWork,
        icon = Icons.Default.Work
    ),
    FREE(
        key = "free",
        label = "Tiempo Libre",
        accentColor = AccentFree,
        containerColor = Color(0xFF132B25),
        badgeBgColor = AccentFree,
        icon = Icons.Default.Restaurant
    );

    companion object {
        fun fromKey(key: String?, title: String = ""): ActivityCategory {
            val keyClean = key?.lowercase()?.trim().orEmpty()
            val titleClean = title.lowercase().trim()

            return when {
                keyClean == "work" || titleClean.contains("trabajo") || titleClean.contains("oficina") -> WORK
                keyClean == "free" || keyClean == "meal" || titleClean.contains("libre") || titleClean.contains("almuerzo") || titleClean.contains("comida") || titleClean.contains("gimnasio") -> FREE
                else -> UNIVERSITY
            }
        }
    }
}
