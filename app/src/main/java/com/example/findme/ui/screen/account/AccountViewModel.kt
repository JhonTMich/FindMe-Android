package com.example.findme.ui.screen.account

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.model.*
import com.example.findme.data.repository.AccountRepository
import com.example.findme.domain.model.validateSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AccountState(
    val initializing: Boolean = true,
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val profile: AccountProfile? = null,
    val schedules: List<ScheduleDto> = emptyList(),
    val statuses: List<StatusOverrideDto> = emptyList(),
    val connections: List<PersonConnection> = emptyList(),
    val profiles: List<AccountProfile> = emptyList(),
    val selectedPerson: AccountProfile? = null,
    val personSchedules: List<ScheduleDto> = emptyList(),
    val personStatuses: List<StatusOverrideDto> = emptyList(),
    val messages: List<QuickMessage> = emptyList(),
    val signals: List<ConnectionSignal> = emptyList(),
    val invitation: ConnectionInvitation? = null
)

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AccountRepository()
    private val _state = MutableStateFlow(AccountState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.auth.sessionStatus.collect {
                val user = repository.auth.currentUserOrNull()
                // Initializing has no user yet; wait until the SDK finishes restoring its session.
                if (it is io.github.jan.supabase.auth.status.SessionStatus.Initializing) return@collect
                if (user == null) _state.value = AccountState(initializing = false)
                else if (_state.value.profile?.id != user.id) {
                    _state.update { old -> old.copy(initializing = false, signedIn = true) }
                    refresh()
                }
            }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("AccountViewModel", "Account operation failed", error)
                _state.update { it.copy(error = "No se pudo completar la operación. Comprueba tu conexión, tus datos y la configuración de cuentas del servidor.") }
            } finally { _state.update { it.copy(busy = false, initializing = false) } }
        }
    }

    fun login(email: String, password: String, name: String, register: Boolean) = perform {
        require(email.contains('@') && password.isNotBlank())
        if (register) repository.register(name, email, password) else repository.signIn(email, password)
        if (repository.auth.currentUserOrNull() == null) {
            _state.update { it.copy(notice = "Revisa tu correo para confirmar la cuenta y después inicia sesión.") }
        } else load()
    }

    private suspend fun load() {
        val profile = repository.ensureProfile()
        val schedules = repository.schedules(profile.id)
        val statuses = repository.overrides(profile.id)
        val connections = repository.connections()
        val profiles = repository.profiles()
        val messages = repository.quickMessages()
        val signals = repository.receivedSignals()
        val selected = _state.value.selectedPerson?.let { old -> profiles.find { it.id == old.id && it.id != profile.id } }
        val personSchedules = selected?.let { repository.schedules(it.id) }.orEmpty()
        val personStatuses = selected?.let { repository.overrides(it.id) }.orEmpty()
        _state.update { it.copy(signedIn = true, profile = profile, schedules = schedules, statuses = statuses,
            connections = connections, profiles = profiles, messages = messages, signals = signals,
            selectedPerson = selected, personSchedules = personSchedules, personStatuses = personStatuses) }
    }

    fun refresh() = perform { load() }
    fun logout() = perform {
        repository.auth.signOut()
        _state.value = AccountState(initializing = false)
        com.example.findme.widget.ConnectionWidget().updateAll(getApplication())
    }
    fun saveSchedule(existingId: String?, day: Int, start: String, end: String, title: String, location: String, category: String, modality: String) {
        val error = validateSchedule(day, start, end, title)
        if (error != null) { _state.update { it.copy(error = error) }; return }
        perform {
            repository.saveSchedule(ScheduleDto(existingId ?: UUID.randomUUID().toString(), repository.userId(), day,
                start.trim(), end.trim(), title.trim(), location.trim(), category, modality))
            load()
            _state.update { it.copy(notice = "Actividad guardada.") }
        }
    }
    fun deleteSchedule(id: String) = perform { repository.deleteSchedule(id); load() }
    fun setStatus(text: String, minutes: Long) = perform { repository.setStatus(text, minutes); load() }
    fun clearStatus() = perform { repository.clearStatus(); load() }
    fun invite() = perform {
        val invitation = repository.createInvitation()
        _state.update { it.copy(invitation = invitation) }
    }
    fun accept(code: String) = perform { repository.acceptInvitation(code); load(); _state.update { it.copy(notice = "Conexión establecida.") } }
    fun disconnect(id: String) = perform { repository.disconnect(id); load() }
    fun selectPerson(profile: AccountProfile) = perform {
        val schedules = repository.schedules(profile.id)
        val statuses = repository.overrides(profile.id)
        _state.update { it.copy(selectedPerson = profile, personSchedules = schedules, personStatuses = statuses) }
    }
    fun saveMessage(label: String, message: String) = perform { repository.saveQuickMessage(label, message); load() }
    fun deleteMessage(id: String) = perform { repository.deleteQuickMessage(id); load() }
    fun configureWidget(message: QuickMessage) {
        val person = _state.value.selectedPerson ?: return
        perform {
            com.example.findme.widget.ConnectionWidgetSettings.save(getApplication(), repository.userId(), person.id, message.id)
            _state.update { it.copy(notice = "Widget configurado para ${person.displayName}. Añade FindMe · Mi gente desde la pantalla de inicio de Android.") }
        }
    }
    fun send(message: QuickMessage) {
        val recipient = _state.value.selectedPerson ?: return
        perform {
            repository.sendSignal(recipient.id, message.message)
            _state.update { it.copy(notice = "Mensaje guardado para ${recipient.displayName}. Puede verlo al actualizar Personas.") }
        }
    }
}
