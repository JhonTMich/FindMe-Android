package com.example.findme.ui.screen

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.local.UserPreferencesRepository
import com.example.findme.data.local.UserSession
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.widget.FindMeControlWidget
import com.example.findme.widget.UserStatusWidget
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

    val isAdminRole: StateFlow<Boolean> = userPrefsRepo.isAdminRoleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
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
