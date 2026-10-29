package com.example.findme.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.ui.components.*
import com.example.findme.ui.screen.profile.ProfileScreen
import com.example.findme.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MainStatusScreen(viewModel: MainViewModel, updateState: UpdateState, onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val status by viewModel.uiState.collectAsStateWithLifecycle()
    val day by viewModel.selectedDay.collectAsStateWithLifecycle()
    val session by viewModel.userSession.collectAsStateWithLifecycle()
    val schedules by viewModel.allSchedulesStream.collectAsStateWithLifecycle()
    val selectedSchedules by viewModel.schedulesForSelectedDay.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val syncing by viewModel.isLoadingSchedules.collectAsStateWithLifecycle()
    val activityAlerts by viewModel.activityAlerts.collectAsStateWithLifecycle()
    val statusAlerts by viewModel.statusAlerts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("hoy") }
    Scaffold(modifier.fillMaxSize(), containerColor = DarkBackground,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(15.dp), color = Color.Transparent) {
                    Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Color(0xFF7370ED), Color(0xFF427FAD)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.NearMe, null, tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FindMe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Hola, ${session?.motherName.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                IconButton(onClick = { viewModel.refresh() }, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Actualizar actividades")
                }
                FilledTonalIconButton(onClick = { tab = "perfil" }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = IndigoCardBg)) {
                    Icon(Icons.Default.PersonOutline, "Abrir perfil", tint = IndigoPrimary)
                }
            }
        }, bottomBar = { BottomNavBar(selectedTabId = tab, onTabSelected = { tab = it }) }) { padding ->
        Crossfade(targetState = tab, modifier = Modifier.fillMaxSize().padding(padding), animationSpec = tween(230), label = "navigation") { activeTab ->
            if (activeTab == "perfil") {
                ProfileScreen(session, activityAlerts, statusAlerts, viewModel::setActivityAlerts, viewModel::setStatusAlerts,
                    updateState, onCheckUpdate, onInstallUpdate, viewModel::logout)
            } else {
                val visible = if (activeTab == "hoy") status.todaySchedules else selectedSchedules
                LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10172A), DarkBackground), endY = 1000f)), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (activeTab == "hoy") {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SIEMPRE CERCA", style = MaterialTheme.typography.labelSmall, color = IndigoPrimary, letterSpacing = 1.sp)
                                Text("Actividades de Hoy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))), color = TextSecondary)
                            }
                        }
                        item { RevealCard { HeroStatusCard(status) } }
                        item { RevealCard(1) { DayOverview(visible) } }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Agenda de hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("${visible.size} actividades en el horario", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { tab = "horario" }) {
                                    Text("Ver semana", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(15.dp))
                                }
                            }
                        }
                    } else {
                        item { WeeklyScheduleHeader(schedules, day, viewModel::onDaySelected) }
                    }
                    if (syncError != null) {
                        item { Text(syncError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (visible.isEmpty()) {
                        item {
                            RevealCard(2) { ScheduleEmptyCard(syncing || status is UserCurrentStatus.Loading) }
                        }
                    }
                    itemsIndexed(visible, key = { _, schedule -> schedule.id.ifBlank { "${schedule.dayOfWeek}-${schedule.startTime}-${schedule.title}" } }) { index, schedule ->
                        RevealCard(index + 2) {
                            ActivityTimelineCard(schedule, activeTab == "hoy" && (status as? UserCurrentStatus.InClass)?.currentSchedule?.id == schedule.id,
                                last = index == visible.lastIndex)
                        }
                    }
                }
            }
        }
    }
}
