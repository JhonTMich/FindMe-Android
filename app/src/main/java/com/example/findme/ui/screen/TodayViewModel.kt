package com.example.findme.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.local.AppDatabase
import com.example.findme.data.local.UserPreferencesRepository
import com.example.findme.data.local.UserSession
import com.example.findme.data.local.toDto
import com.example.findme.data.repository.ScheduleRepository
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val scheduleRepository = ScheduleRepository(db.scheduleDao())
    private val userPrefsRepo = UserPreferencesRepository(application)
    private val todayLimaDay = ZonedDateTime.now(StatusRepository.SYSTEM_ZONE).dayOfWeek.value

    val userSession: StateFlow<UserSession?> = userPrefsRepo.userSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    init {
        viewModelScope.launch {
            userSession.collect { session ->
                if (session != null) {
                    syncScheduleFromNetwork(session.userId)
                }
            }
        }
    }

    fun syncScheduleFromNetwork(userId: String) {
        viewModelScope.launch {
            scheduleRepository.syncScheduleFromNetwork(userId)
        }
    }

    val todayStatus: StateFlow<UserCurrentStatus> = userSession.flatMapLatest { session ->
        if (session != null) {
            scheduleRepository.getWeeklyScheduleStream(session.userId).flatMapLatest { entities ->
                val dtos = entities.map { it.toDto() }
                StatusRepository.observeCurrentStatusWithSchedules(
                    userId = session.userId,
                    displayName = session.displayName,
                    schedules = dtos,
                    selectedDayOfWeek = todayLimaDay
                )
            }
        } else {
            MutableStateFlow(UserCurrentStatus.Loading(selectedDayOfWeek = todayLimaDay))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserCurrentStatus.Loading(selectedDayOfWeek = todayLimaDay)
    )
}
