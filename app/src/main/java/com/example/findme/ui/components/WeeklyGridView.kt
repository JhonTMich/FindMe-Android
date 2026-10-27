package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.IndigoPrimary
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

enum class GridSlotType {
    CLASS,       // Purple/Indigo
    STUDY,       // Dark Indigo
    ACTIVE,      // Highlighted Indigo with live dot
    GYM,         // Emerald Green
    FREE,        // Grey outline
    LUNCH        // Yellow full width
}

data class GridSlotData(
    val title: String,
    val subtitle: String,
    val type: GridSlotType
)

@Composable
fun WeeklyGridView(
    onSlotClick: (GridSlotData) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
            ) {
                Column {
                    // 1. Grid Header Row (Time Icon + Days 23, 24, 25, 26)
                    Row(
                        modifier = Modifier.padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time column header
                        Box(
                            modifier = Modifier.width(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Day 23 (Lun)
                        GridHeaderCell(shortName = "Lun", dayNum = "23", isSelected = false)
                        Spacer(modifier = Modifier.width(6.dp))

                        // Day 24 (Mar)
                        GridHeaderCell(shortName = "Mar", dayNum = "24", isSelected = false)
                        Spacer(modifier = Modifier.width(6.dp))

                        // Day 25 (Mié - Selected)
                        GridHeaderCell(shortName = "Mié", dayNum = "25", isSelected = true)
                        Spacer(modifier = Modifier.width(6.dp))

                        // Day 26 (Jue)
                        GridHeaderCell(shortName = "Jue", dayNum = "26", isSelected = false)
                    }

                    // 2. 08:00 Row
                    GridTimeRow(
                        timeText = "08:00",
                        slots = listOf(
                            GridSlotData("Algoritmos", "Aula 302", GridSlotType.CLASS),
                            GridSlotData("Cardio / ...", "Fitness Hub", GridSlotType.GYM),
                            GridSlotData("Algoritmos", "Aula 302", GridSlotType.CLASS),
                            GridSlotData("Crossfit", "Box Centro", GridSlotType.GYM)
                        ),
                        onSlotClick = onSlotClick
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. 10:00 Row
                    GridTimeRow(
                        timeText = "10:00",
                        slots = listOf(
                            GridSlotData("Repaso...", "Biblio Central", GridSlotType.STUDY),
                            GridSlotData("Bases...", "Aula 104", GridSlotType.CLASS),
                            GridSlotData("Bases...", "Aula 104", GridSlotType.CLASS),
                            GridSlotData("Laboratorio...", "Lab B", GridSlotType.CLASS)
                        ),
                        onSlotClick = onSlotClick
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 4. 12:00 Lunch Row (Full-width Banner across all days)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.width(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "12:00",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2B220B),
                            border = BorderStroke(1.dp, Color(0xFF524110)),
                            modifier = Modifier
                                .width(330.dp)
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Almuerzo & Tiempo Libre (12:30 - 14:00)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 5. 14:00 Row
                    GridTimeRow(
                        timeText = "14:00",
                        slots = listOf(
                            GridSlotData("Taller Web", "Online", GridSlotType.CLASS),
                            GridSlotData("Libre", "", GridSlotType.FREE),
                            GridSlotData("Estudio ...", "Casa • Activo", GridSlotType.ACTIVE),
                            GridSlotData("Física II", "Aula 201", GridSlotType.CLASS)
                        ),
                        onSlotClick = onSlotClick
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 6. 16:00 Row
                    GridTimeRow(
                        timeText = "16:00",
                        slots = listOf(
                            GridSlotData("Libre", "", GridSlotType.FREE),
                            GridSlotData("Grupo...", "Meet Online", GridSlotType.CLASS),
                            GridSlotData("Frontend...", "Casa (Hasta 18:30)", GridSlotType.STUDY),
                            GridSlotData("Investigación", "Biblio", GridSlotType.CLASS)
                        ),
                        onSlotClick = onSlotClick
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 7. 18:00 Row
                    GridTimeRow(
                        timeText = "18:00",
                        slots = listOf(
                            GridSlotData("Natación", "Club Acuático", GridSlotType.GYM),
                            GridSlotData("Gimnasio", "Pesas", GridSlotType.GYM),
                            GridSlotData("Gimnasio", "19:00 - 20:30", GridSlotType.GYM),
                            GridSlotData("Natación", "Club Acuático", GridSlotType.GYM)
                        ),
                        onSlotClick = onSlotClick
                    )
                }
            }
        }
    }
}

@Composable
private fun GridHeaderCell(
    shortName: String,
    dayNum: String,
    isSelected: Boolean
) {
    val bg = if (isSelected) Color(0xFF262A58) else Color(0xFF131A2B)
    val border = if (isSelected) IndigoPrimary else Color(0xFF1E283D)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .width(78.dp)
            .height(50.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFFA5B4FC), CircleShape)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = "$shortName ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) Color.White else TextMuted
            )
            Text(
                text = dayNum,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else TextPrimary
            )
        }
    }
}

@Composable
private fun GridTimeRow(
    timeText: String,
    slots: List<GridSlotData>,
    onSlotClick: (GridSlotData) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Time Label Column
        Box(
            modifier = Modifier.width(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }

        // Slot Cells for the 4 days
        slots.forEachIndexed { idx, slot ->
            GridSlotCell(slot = slot, onClick = { onSlotClick(slot) })
            if (idx < slots.size - 1) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun GridSlotCell(
    slot: GridSlotData,
    onClick: () -> Unit
) {
    val (bg, border, titleColor, subColor) = when (slot.type) {
        GridSlotType.CLASS -> Quadruple(
            Color(0xFF241D44),
            Color(0xFF3B2F6E),
            TextPrimary,
            Color(0xFFA5B4FC)
        )
        GridSlotType.STUDY -> Quadruple(
            Color(0xFF1D2447),
            Color(0xFF333E78),
            TextPrimary,
            Color(0xFF818CF8)
        )
        GridSlotType.ACTIVE -> Quadruple(
            Color(0xFF3C3888),
            IndigoPrimary,
            Color.White,
            Color(0xFFC7D2FE)
        )
        GridSlotType.GYM -> Quadruple(
            Color(0xFF0D2820),
            Color(0xFF184D3E),
            Color(0xFF34D399),
            Color(0xFF6EE7B7)
        )
        GridSlotType.FREE -> Quadruple(
            Color(0xFF121827),
            Color(0xFF26334D),
            TextMuted,
            TextMuted
        )
        GridSlotType.LUNCH -> Quadruple(
            Color(0xFF2B220B),
            Color(0xFF524110),
            Color(0xFFFBBF24),
            Color(0xFFFDE68A)
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(if (slot.type == GridSlotType.ACTIVE) 1.5.dp else 1.dp, border),
        modifier = Modifier
            .width(78.dp)
            .height(58.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = slot.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (slot.type == GridSlotType.ACTIVE) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                }
            }

            if (slot.subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = slot.subtitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
