package com.example.findme.ui.screen

import android.app.Application
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.local.AppDatabase
import com.example.findme.data.local.UserPreferencesRepository
import com.example.findme.data.local.UserSession
import com.example.findme.data.local.toDto
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.repository.ScheduleRepository
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.util.NotificationHelper
import com.example.findme.util.ScheduleNotifier
import com.example.findme.widget.ConnectionWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val scheduleRepository = ScheduleRepository(db.scheduleDao())
    private val userPrefsRepo = UserPreferencesRepository(application)
    val activityAlerts = userPrefsRepo.activityAlerts.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val statusAlerts = userPrefsRepo.statusAlerts.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setActivityAlerts(enabled: Boolean) { viewModelScope.launch { userPrefsRepo.setActivityAlerts(enabled) } }
    fun setStatusAlerts(enabled: Boolean) { viewModelScope.launch { userPrefsRepo.setStatusAlerts(enabled) } }
    private val systemDay = flow {
        while (true) {
            emit(java.time.LocalDate.now().dayOfWeek.value)
            delay(15_000)
        }
    }.distinctUntilChanged()
    private val todayLimaDay = ZonedDateTime.now(StatusRepository.SYSTEM_ZONE).dayOfWeek.value

    val userSession: StateFlow<UserSession?> = userPrefsRepo.userSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val isAdminRole: StateFlow<Boolean> = userPrefsRepo.isAdminRoleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _selectedDay = MutableStateFlow(todayLimaDay)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError.asStateFlow()

    private val _isSyncingSchedules = MutableStateFlow(false)
    val isLoadingSchedules: StateFlow<Boolean> = _isSyncingSchedules.asStateFlow()

    // Stream all schedules from local Room DB with 0 ms latency
    val allSchedulesStream: StateFlow<List<ScheduleDto>> = userSession.flatMapLatest { session ->
        if (session != null) {
            scheduleRepository.getWeeklyScheduleStream(session.userId).map { entities ->
                entities.map { it.toDto() }
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered schedules for selected day (0 ms latency on day selection)
    val schedulesForSelectedDay: StateFlow<List<ScheduleDto>> = combine(allSchedulesStream, _selectedDay) { list, day ->
        list.filter { it.dayOfWeek == day }
            .sortedBy { StatusRepository.parseLocalTimeOrNull(it.startTime) ?: LocalTime.MIN }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<UserCurrentStatus> = combine(userSession, systemDay, allSchedulesStream) { session, day, schedules ->
        Triple(session, day, schedules)
    }.flatMapLatest { (session, day, schedules) ->
        if (session != null) {
            StatusRepository.observeCurrentStatusWithSchedules(
                userId = session.userId,
                displayName = session.displayName,
                schedules = schedules,
                selectedDayOfWeek = day
            )
        } else {
            MutableStateFlow(UserCurrentStatus.Loading(selectedDayOfWeek = day))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserCurrentStatus.Loading(selectedDayOfWeek = todayLimaDay)
    )

    init {
        // Observe userSession and immediately sync from network in background
        viewModelScope.launch {
            userSession.collect { session ->
                if (session != null) {
                    syncScheduleFromNetwork(session.userId)
                }
            }
        }

        // Setup local push notification observer for monitor role
        viewModelScope.launch {
            var previousState: UserCurrentStatus? = null
            uiState.collect { state ->
                val wasAdmin = isAdminRole.value
                if (!wasAdmin && statusAlerts.value && previousState != null) {
                    val prevOverride = previousState as? UserCurrentStatus.Override
                    val currOverride = state as? UserCurrentStatus.Override

                    if (currOverride != null && prevOverride?.customLocation != currOverride.customLocation) {
                        NotificationHelper.showStatusNotification(
                            getApplication(),
                            "Actualización de Estado",
                            "${state.displayName} está ahora: ${currOverride.customLocation}"
                        )
                    } else if (prevOverride != null && currOverride == null && state !is UserCurrentStatus.Loading) {
                        NotificationHelper.showStatusNotification(
                            getApplication(),
                            "Actualización de Estado",
                            when (state) {
                                is UserCurrentStatus.InClass -> "${state.displayName}: ${state.currentSchedule.title} está en curso."
                                else -> "${state.displayName}: ya no hay un estado compartido activo."
                            }
                        )
                    }
                }
                if (state !is UserCurrentStatus.Loading) {
                    previousState = state
                }
            }
        }

        // Programar alertas de Room (WorkManager)
        viewModelScope.launch {
            combine(allSchedulesStream, activityAlerts, isAdminRole, userSession) { schedules, enabled, admin, session ->
                if (enabled && !admin && session != null) schedules else emptyList()
            }.collect { schedules ->
                if (schedules.isNotEmpty()) {
                    ScheduleNotifier.scheduleAlertsForSchedules(getApplication(), schedules)
                } else {
                    WorkManager.getInstance(getApplication()).cancelAllWorkByTag("class_alert")
                }
            }
        }
    }

    fun syncScheduleFromNetwork(userId: String) {
        viewModelScope.launch {
            _isSyncingSchedules.value = true
            _syncError.value = null
            try {
                val result = scheduleRepository.syncScheduleFromNetwork(userId)
                if (userSession.value?.userId == userId && result.isFailure) {
                    _syncError.value = "No se pudo sincronizar. Se muestra el horario guardado; pulsa actualizar para reintentar."
                }
            } finally {
                _isSyncingSchedules.value = false
            }
        }
    }

    fun authenticateAdmin(password: String): Boolean {
        return if (password == UserPreferencesRepository.ADMIN_PASSWORD) {
            setAdminRole(true)
            true
        } else {
            false
        }
    }

    fun setAdminRole(isAdmin: Boolean) {
        viewModelScope.launch {
            userPrefsRepo.setAdminRole(isAdmin)
            updateWidgets()
        }
    }

    fun setOnTheWay() {
        val session = userSession.value ?: return
        viewModelScope.launch {
            StatusRepository.setOnTheWayOverride(session.userId)
            updateWidgets()
        }
    }

    fun setOnTheStreet() {
        val session = userSession.value ?: return
        viewModelScope.launch {
            StatusRepository.setOnTheStreetOverride(session.userId)
            updateWidgets()
        }
    }

    fun setArrivedHome() {
        val session = userSession.value ?: return
        viewModelScope.launch {
            StatusRepository.clearOverrides(session.userId)
            updateWidgets()
        }
    }

    private suspend fun updateWidgets() {
        try {
            ConnectionWidget().updateAll(getApplication())
        } catch (_: Exception) {}
    }

    fun onDaySelected(dayOfWeek: Int) {
        _selectedDay.value = dayOfWeek
    }

    fun refresh() {
        val session = userSession.value ?: return
        syncScheduleFromNetwork(session.userId)
    }

    fun logout() {
        viewModelScope.launch {
            val currentUserId = userSession.value?.userId
            if (!currentUserId.isNullOrEmpty()) {
                try {
                    scheduleRepository.clearUserSchedule(currentUserId)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error clearing user schedule on logout", e)
                }
            }
            userPrefsRepo.clearSession()
            updateWidgets()
        }
    }
}
