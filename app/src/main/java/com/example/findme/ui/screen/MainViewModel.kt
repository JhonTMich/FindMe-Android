package com.example.findme.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.local.UserPreferencesRepository
import com.example.findme.data.local.UserSession
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefsRepo = UserPreferencesRepository(application)
    private val todayLimaDay = ZonedDateTime.now(StatusRepository.LIMA_ZONE).dayOfWeek.value

    val userSession: StateFlow<UserSession?> = userPrefsRepo.userSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val _selectedDay = MutableStateFlow(todayLimaDay)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    val uiState: StateFlow<UserCurrentStatus> = combine(userSession, _selectedDay) { session, day ->
        Pair(session, day)
    }.flatMapLatest { (session, day) ->
        if (session != null) {
            StatusRepository.observeCurrentStatus(
                userId = session.userId,
                displayName = session.displayName,
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

    fun onDaySelected(dayOfWeek: Int) {
        _selectedDay.value = dayOfWeek
    }

    fun refresh() {
        viewModelScope.launch {
            val currentDay = _selectedDay.value
            _selectedDay.value = currentDay
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPrefsRepo.clearSession()
        }
    }
}
