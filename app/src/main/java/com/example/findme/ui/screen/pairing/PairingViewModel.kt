package com.example.findme.ui.screen.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.local.UserPreferencesRepository
import com.example.findme.data.repository.StatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PairingUiState(
    val familyCodeInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefsRepo = UserPreferencesRepository(application)

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun onCodeChanged(newCode: String) {
        _uiState.value = _uiState.value.copy(
            familyCodeInput = newCode.uppercase(),
            errorMessage = null
        )
    }

    fun submitPairingCode() {
        val code = _uiState.value.familyCodeInput.trim()
        if (code.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Por favor ingresa un código familiar")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = StatusRepository.validateFamilyCode(code)
            result.fold(
                onSuccess = { profile ->
                    userPrefsRepo.saveUserSession(
                        userId = profile.id,
                        displayName = profile.displayName,
                        familyCode = profile.familyCode
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Error al vincular con el código"
                    )
                }
            )
        }
    }
}
