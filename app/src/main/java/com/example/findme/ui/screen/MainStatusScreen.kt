package com.example.findme.ui.screen

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
import androidx.compose.ui.graphics.Color
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
import java.time.Duration
import java.time.ZonedDateTime

private val MainBg = Color(0xFFF3F8F8)

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
    val inClassStatus = uiState as? UserCurrentStatus.InClass
    val currentScheduleId = inClassStatus?.currentSchedule?.id

    val nextSchedule = if (currentScheduleId != null) {
        val currentIndex = schedules.indexOfFirst { it.id == currentScheduleId }
        if (currentIndex != -1 && currentIndex + 1 < schedules.size) {
            schedules[currentIndex + 1]
        } else null
    } else {
        val nowInLima = ZonedDateTime.now(StatusRepository.LIMA_ZONE).toLocalTime()
        schedules.firstOrNull { schedule ->
            val start = StatusRepository.parseLocalTimeOrNull(schedule.startTime)
            start != null && !start.isBefore(nowInLima)
        }
    }

    val followingSchedule = if (nextSchedule != null) {
        val nextIndex = schedules.indexOfFirst { it.id == nextSchedule.id }
        if (nextIndex != -1 && nextIndex + 1 < schedules.size) {
            schedules[nextIndex + 1]
        } else null
    } else null

    val minutesUntilNext = if (inClassStatus != null) {
        inClassStatus.remainingMinutes
    } else if (nextSchedule != null) {
        val nowInLima = ZonedDateTime.now(StatusRepository.LIMA_ZONE).toLocalTime()
        val start = StatusRepository.parseLocalTimeOrNull(nextSchedule.startTime)
        if (start != null) {
            (Duration.between(nowInLima, start).seconds / 60).coerceAtLeast(0)
        } else null
    } else null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MainBg,
        topBar = {
            FindMeTopHeader(
                displayName = uiState.displayName,
                onRefresh = { viewModel.refresh() },
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
                                        color = Color(0xFF212121)
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFECEFF1)
                                    ) {
                                        Text(
                                            text = "${schedules.size} materias",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF455A64),
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
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "🎉 ¡Día Libre!",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF424242)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "No hay actividades registradas para este día.",
                                                fontSize = 13.sp,
                                                color = Color.Gray
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
