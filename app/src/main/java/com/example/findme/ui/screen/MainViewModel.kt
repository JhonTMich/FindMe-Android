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
import com.example.findme.widget.FindMeControlWidget
import com.example.findme.widget.UserStatusWidget
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
import java.time.LocalTime
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val scheduleRepository = ScheduleRepository(db.scheduleDao())
    private val userPrefsRepo = UserPreferencesRepository(application)
    private val todayLimaDay = ZonedDateTime.now(StatusRepository.LIMA_ZONE).dayOfWeek.value

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

    val uiState: StateFlow<UserCurrentStatus> = combine(userSession, _selectedDay, allSchedulesStream) { session, day, schedules ->
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
                if (!wasAdmin && previousState != null) {
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
                            "${state.displayName} ha llegado a su destino (Ya llegué)."
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
            allSchedulesStream.collect { schedules ->
                if (schedules.isNotEmpty() && !isAdminRole.value) {
                    ScheduleNotifier.scheduleAlertsForSchedules(getApplication(), schedules)
                }
            }
        }
    }

    fun syncScheduleFromNetwork(userId: String) {
        viewModelScope.launch {
            _isSyncingSchedules.value = true
            scheduleRepository.syncScheduleFromNetwork(userId)
            _isSyncingSchedules.value = false
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
            UserStatusWidget().updateAll(getApplication())
            FindMeControlWidget().updateAll(getApplication())
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
