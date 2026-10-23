package com.example.findme.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.ui.components.BottomNavBar
import com.example.findme.ui.components.CronogramaHoyCard
import com.example.findme.ui.components.DayScheduleSelector
import com.example.findme.ui.components.FindMeTopHeader
import com.example.findme.ui.components.HeroStatusCard
import com.example.findme.ui.components.NextActivityCard
import com.example.findme.ui.components.ScheduleItemCard
import com.example.findme.ui.screen.profile.ProfileScreen
import com.example.findme.ui.theme.DarkBackground
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary
import java.time.ZonedDateTime

@Composable
fun MainStatusScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()

    var selectedTabId by remember { mutableStateOf("hoy") }

    val schedules = uiState.todaySchedules

    // Calculation for "PRÓXIMA ACTIVIDAD"
    val nowInLima = ZonedDateTime.now(StatusRepository.LIMA_ZONE).toLocalTime()
    val todayLimaDay = ZonedDateTime.now(StatusRepository.LIMA_ZONE).dayOfWeek.value

    val futureTodaySchedules = if (selectedDay == todayLimaDay) {
        schedules.filter { schedule ->
            val start = StatusRepository.parseLocalTimeOrNull(schedule.startTime)
            start != null && start.isAfter(nowInLima)
        }.sortedBy { StatusRepository.parseLocalTimeOrNull(it.startTime) }
    } else emptyList()

    val nextSchedule = futureTodaySchedules.firstOrNull()
    val followingSchedule = futureTodaySchedules.getOrNull(1)

    val minutesUntilNext = if (nextSchedule != null) {
        val start = StatusRepository.parseLocalTimeOrNull(nextSchedule.startTime)
        if (start != null) {
            val startMinutes = start.hour * 60 + start.minute
            val currentMinutes = nowInLima.hour * 60 + nowInLima.minute
            (startMinutes - currentMinutes).toLong().coerceAtLeast(0)
        } else null
    } else null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            FindMeTopHeader(
                motherName = userSession?.motherName ?: "Mamá",
                onLogout = { viewModel.logout() }
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedTabId = selectedTabId,
                onTabSelected = { selectedTabId = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabId) {
                "hoy" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Hero Status Card
                        item {
                            HeroStatusCard(status = uiState)
                        }

                        // 2. Próxima Actividad Card
                        if (nextSchedule != null) {
                            item {
                                NextActivityCard(
                                    nextSchedule = nextSchedule,
                                    followingSchedule = followingSchedule,
                                    minutesUntilNext = minutesUntilNext
                                )
                            }
                        }

                        // 3. Cronograma de hoy Card
                        item {
                            CronogramaHoyCard(
                                schedules = schedules,
                                onVerHorarioCompleto = { selectedTabId = "horario" }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                "horario" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Horario Semanal UPAO",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = DarkCardBg,
                                        border = BorderStroke(1.dp, DarkCardBorder)
                                    ) {
                                        Text(
                                            text = "${schedules.size} Actividades",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                DayScheduleSelector(
                                    selectedDayOfWeek = selectedDay,
                                    onDaySelected = { day -> viewModel.onDaySelected(day) }
                                )
                            }
                        }

                        if (schedules.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                    border = BorderStroke(1.dp, DarkCardBorder)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "No hay actividades registradas para este día.",
                                                fontSize = 13.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            items(schedules, key = { it.id.ifEmpty { it.title + it.startTime } }) { schedule ->
                                val isCurrentInClass = (uiState as? UserCurrentStatus.InClass)?.currentSchedule?.id == schedule.id
                                ScheduleItemCard(
                                    schedule = schedule,
                                    isActiveClass = isCurrentInClass
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                "perfil" -> {
                    ProfileScreen(
                        userSession = userSession,
                        onLogout = { viewModel.logout() }
                    )
                }
            }
        }
    }
}
