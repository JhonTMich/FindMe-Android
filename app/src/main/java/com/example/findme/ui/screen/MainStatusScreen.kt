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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.ui.components.AdminQuickActionsCard
import com.example.findme.ui.components.BottomNavBar
import com.example.findme.ui.components.CronogramaHoyCard
import com.example.findme.ui.components.FindMeTopHeader
import com.example.findme.ui.components.HeroStatusCard
import com.example.findme.ui.components.ScheduleItemCard
import com.example.findme.ui.components.ScheduleTimelineCard
import com.example.findme.ui.components.WeeklyScheduleHeader
import com.example.findme.ui.screen.profile.ProfileScreen
import com.example.findme.ui.theme.AccentWork
import com.example.findme.ui.theme.DarkBackground
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.TextMuted

@Composable
fun MainStatusScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val isAdminRole by viewModel.isAdminRole.collectAsStateWithLifecycle()
    val allSchedules by viewModel.allSchedulesStream.collectAsStateWithLifecycle()
    val schedulesForSelectedDay by viewModel.schedulesForSelectedDay.collectAsStateWithLifecycle()
    val isLoadingSchedules by viewModel.isLoadingSchedules.collectAsStateWithLifecycle()

    var selectedTabId by remember { mutableStateOf("hoy") }
    var isAgendaView by remember { mutableStateOf(true) }

    val schedules = uiState.todaySchedules
    val currentScheduleInClass = (uiState as? UserCurrentStatus.InClass)?.currentSchedule

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            FindMeTopHeader(
                motherName = userSession?.motherName ?: "Mamá",
                isSyncing = isLoadingSchedules,
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Hero Status Card ("Michael", "En Casa", "PUNTOS DE FRECUENCIA")
                        item {
                            HeroStatusCard(
                                status = uiState,
                                onSelectPoint = { point ->
                                    when (point) {
                                        "Calle" -> viewModel.setOnTheStreet()
                                        "Casa" -> viewModel.setArrivedHome()
                                        "Universidad" -> viewModel.setOnTheWay()
                                    }
                                }
                            )
                        }

                        // 1b. Admin Quick Actions Card (if admin)
                        if (isAdminRole) {
                            item {
                                AdminQuickActionsCard(
                                    onSetOnTheWay = { viewModel.setOnTheWay() },
                                    onSetOnTheStreet = { viewModel.setOnTheStreet() },
                                    onSetArrivedHome = { viewModel.setArrivedHome() }
                                )
                            }
                        }

                        // 2. Cronograma de hoy Header & Progress Card
                        item {
                            CronogramaHoyCard(
                                schedules = schedules,
                                onVerHorarioCompleto = { selectedTabId = "horario" }
                            )
                        }

                        // 3. Today Timeline List Card
                        item {
                            ScheduleTimelineCard(schedules = schedules)
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                "horario" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Weekly Schedule Header (Tag, Bar Chart, Month, Day Pills, Legend, Mode Toggle, Day Title)
                        item {
                            WeeklyScheduleHeader(
                                allSchedules = allSchedules,
                                selectedDayOfWeek = selectedDay,
                                onDaySelected = { day -> viewModel.onDaySelected(day) },
                                isAgendaView = isAgendaView,
                                onToggleViewMode = { isAgendaView = it }
                            )
                        }

                        // 2. Schedule Cards List (Dynamic from DB with mock fallback matching design)
                        val displaySchedules = if (schedulesForSelectedDay.isNotEmpty()) {
                            schedulesForSelectedDay
                        } else {
                            getSampleSchedulesForDay(selectedDay)
                        }

                        if (isLoadingSchedules && schedulesForSelectedDay.isEmpty()) {
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
                                        CircularProgressIndicator(
                                            color = AccentWork,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            displaySchedules.forEachIndexed { index, schedule ->
                                // Optional Meal Banner Insertion around 12:00
                                if (index == 2 && schedule.startTime >= "14:00") {
                                    item {
                                        MealBreakBanner()
                                    }
                                }

                                item(key = schedule.id.ifEmpty { schedule.title + schedule.startTime }) {
                                    val isCurrentInClass = currentScheduleInClass?.id == schedule.id
                                    ScheduleItemCard(
                                        schedule = schedule,
                                        isActiveClass = isCurrentInClass,
                                        onCheckIn = { viewModel.setOnTheWay() },
                                        onDetails = { }
                                    )
                                }
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
                        isAdminRole = isAdminRole,
                        onAuthenticateAdmin = { password -> viewModel.authenticateAdmin(password) },
                        onToggleAdminMode = { isAdmin -> viewModel.setAdminRole(isAdmin) },
                        onSetOnTheWay = { viewModel.setOnTheWay() },
                        onSetOnTheStreet = { viewModel.setOnTheStreet() },
                        onSetArrivedHome = { viewModel.setArrivedHome() },
                        onLogout = { viewModel.logout() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MealBreakBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2B220B),
        border = BorderStroke(1.dp, Color(0xFF524110)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Almuerzo & Descanso programado",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24)
                )
            }

            Text(
                text = "12:00 — 14:30",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }
    }
}

private fun getSampleSchedulesForDay(dayOfWeek: Int): List<ScheduleDto> {
    return listOf(
        ScheduleDto(
            id = "mock_1",
            dayOfWeek = dayOfWeek,
            startTime = "08:00:00",
            endTime = "09:45:00",
            title = "Algoritmos y Estructuras de Datos",
            location = "Aula 302 • Pabellón de Sistemas",
            category = "university",
            modality = "Presencial"
        ),
        ScheduleDto(
            id = "mock_2",
            dayOfWeek = dayOfWeek,
            startTime = "10:00:00",
            endTime = "12:00:00",
            title = "Bases de Datos Relacionales y NoSQL",
            location = "Aula 104 • Edificio Central",
            category = "university",
            modality = "Presencial"
        ),
        ScheduleDto(
            id = "mock_3",
            dayOfWeek = dayOfWeek,
            startTime = "15:00:00",
            endTime = "18:30:00",
            title = "Estudio en Casa — Proyecto Frontend UI",
            location = "Casa / Estudio",
            category = "work",
            modality = "Remoto / Foco"
        ),
        ScheduleDto(
            id = "mock_4",
            dayOfWeek = dayOfWeek,
            startTime = "19:00:00",
            endTime = "20:30:00",
            title = "Cardio & Entrenamiento de Fuerza",
            location = "Gimnasio",
            category = "free",
            modality = "Presencial"
        )
    )
}
