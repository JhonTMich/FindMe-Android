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
    val selectedRole: String? = null,
    val motherNameInput: String = "",
    val familyCodeInput: String = "",
    val adminPasswordInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefsRepo = UserPreferencesRepository(application)

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun onRoleSelected(role: String?) {
        _uiState.value = _uiState.value.copy(
            selectedRole = role,
            errorMessage = null
        )
    }

    fun onMotherNameChanged(newName: String) {
        _uiState.value = _uiState.value.copy(
            motherNameInput = newName,
            errorMessage = null
        )
    }

    fun onCodeChanged(newCode: String) {
        _uiState.value = _uiState.value.copy(
            familyCodeInput = newCode.uppercase(),
            errorMessage = null
        )
    }

    fun onAdminPasswordChanged(newPassword: String) {
        _uiState.value = _uiState.value.copy(
            adminPasswordInput = newPassword,
            errorMessage = null
        )
    }

    fun submitPairingCode() {
        val role = _uiState.value.selectedRole
        if (role == "ADMIN") {
            submitAdminLogin()
        } else {
            submitMonitorLogin()
        }
    }

    private fun submitAdminLogin() {
        val password = _uiState.value.adminPasswordInput.trim()
        if (password.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Por favor ingresa la contraseña maestra")
            return
        }
        
        if (password != UserPreferencesRepository.ADMIN_PASSWORD) {
            _uiState.value = _uiState.value.copy(errorMessage = "Contraseña incorrecta")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = StatusRepository.getAdminProfile()
            result.fold(
                onSuccess = { profile ->
                    userPrefsRepo.saveUserSession(
                        userId = profile.id,
                        displayName = profile.displayName,
                        familyCode = profile.familyCode,
                        motherName = ""
                    )
                    userPrefsRepo.setAdminRole(true)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Error al obtener perfil administrador"
                    )
                }
            )
        }
    }

    private fun submitMonitorLogin() {
        val motherName = _uiState.value.motherNameInput.trim()
        val code = _uiState.value.familyCodeInput.trim()

        if (motherName.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Por favor ingresa cómo te llamas")
            return
        }

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
                        familyCode = profile.familyCode,
                        motherName = motherName
                    )
                    userPrefsRepo.setAdminRole(false)
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
