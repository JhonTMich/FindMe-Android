package com.example.findme.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.BuildConfig
import com.example.findme.data.repository.AppRelease
import com.example.findme.data.repository.AppUpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateState(
    val checking: Boolean = false,
    val release: AppRelease? = null,
    val message: String = "Se comprueban las actualizaciones al abrir la app.",
    val showDialog: Boolean = false
)

class UpdateViewModel : ViewModel() {
    private val repository = AppUpdateRepository()
    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()
    init { check() }
    fun check() {
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true)
        viewModelScope.launch {
            try {
                val release = repository.latest(BuildConfig.VERSION_NAME)
                _state.value = UpdateState(
                    release = release,
                    message = if (release == null) "Tienes la versión más reciente." else "Versión ${release.version} disponible.",
                    showDialog = release != null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(checking = false, message =
                    if (error is java.io.IOException) "No se pudo conectar. Revisa tu conexión e intenta de nuevo."
                    else error.message ?: "No se pudo comprobar la actualización.")
            }
        }
    }
    fun dismiss() { _state.value = _state.value.copy(showDialog = false) }
}
