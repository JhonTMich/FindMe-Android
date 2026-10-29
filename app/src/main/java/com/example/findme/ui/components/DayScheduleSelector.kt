package com.example.findme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.repository.StatusRepository
import com.example.findme.ui.theme.AccentUniversity
import com.example.findme.ui.theme.AccentWork
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.TextSecondary
import java.time.ZonedDateTime

data class DayItem(
    val dayOfWeek: Int, // 1=Lunes ... 7=Domingo
    val shortName: String,
    val fullName: String,
    val isToday: Boolean
)

@Composable
fun DayScheduleSelector(
    selectedDayOfWeek: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayLimaDay = ZonedDateTime.now(StatusRepository.SYSTEM_ZONE).dayOfWeek.value

    val days = listOf(
        DayItem(1, "LUN", "Lunes", todayLimaDay == 1),
        DayItem(2, "MAR", "Martes", todayLimaDay == 2),
        DayItem(3, "MIÉ", "Miércoles", todayLimaDay == 3),
        DayItem(4, "JUE", "Jueves", todayLimaDay == 4),
        DayItem(5, "VIE", "Viernes", todayLimaDay == 5),
        DayItem(6, "SÁB", "Sábado", todayLimaDay == 6),
        DayItem(7, "DOM", "Domingo", todayLimaDay == 7)
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(days, key = { it.dayOfWeek }) { day ->
            val isSelected = day.dayOfWeek == selectedDayOfWeek

            val cardBg = if (isSelected) AccentWork else DarkCardBg
            val textColor = if (isSelected) Color.Black else TextSecondary
            val borderModifier = if (isSelected) Modifier else Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(borderModifier)
                    .background(cardBg)
                    .clickable { onDaySelected(day.dayOfWeek) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (day.isToday) {
                        Surface(
                            shape = CircleShape,
                            color = AccentUniversity
                        ) {
                            Text(
                                text = "HOY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = day.shortName,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}
