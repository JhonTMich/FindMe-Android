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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.findme.data.local.*
import com.example.findme.data.repository.AccountLiveUpdates
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.util.NotificationHelper
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

data class AccountState(
    val settings: AccountSettings = AccountSettings(),
    val lastSync: Instant? = null,
    val syncIssue: Boolean = false,
    val peopleRevision: Int = 0,
    val initializing: Boolean = true,
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val confirmationEmail: String? = null,
    val loginRevision: Int = 0,
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

class AccountViewModel(application: Application) : AndroidViewModel(application), AccountActions {
    private val repository = AccountRepository()
    private val preferences = AccountSettingsRepository(application)
    private val snapshotMutex = Mutex()
    private var baselineOwner: String? = null
    private val messageNotifications = com.example.findme.domain.model.MessageNotificationTracker()
    private var knownStatuses = mutableMapOf<String, String>()
    private var allSchedules: List<ScheduleDto> = emptyList()
    private var allStatuses: List<StatusOverrideDto> = emptyList()
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

    init {
        viewModelScope.launch {
            repository.auth.sessionStatus.collectLatest {
                val id = repository.auth.currentUserOrNull()?.id ?: return@collectLatest
                preferences.settings(id).collect { settings ->
                    if (repository.auth.currentUserOrNull()?.id == id) _state.update { old -> old.copy(settings = settings) }
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
            catch (error: IllegalArgumentException) {
                _state.update { it.copy(error = error.message ?: "Revisa los datos ingresados.") }
            }
            catch (error: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
                _state.update { it.copy(error = if (error.code == "23505") "Ese nombre de usuario ya está ocupado. Elige otro." else "No se pudieron guardar los datos. Inténtalo de nuevo.") }
            }
            catch (error: Exception) {
                android.util.Log.w("AccountViewModel", "Account operation failed", error)
                _state.update { it.copy(error = "No se pudo completar la operación. Comprueba tu conexión, tus datos y la configuración de cuentas del servidor.") }
            } finally { _state.update { it.copy(busy = false, initializing = false) } }
        }
    }

    override fun login(username: String, password: String) = perform {
        require(com.example.findme.domain.model.Username.isValid(username) && password.isNotBlank()) { "Introduce un usuario válido y tu contraseña." }
        repository.signIn(username, password)
        if (repository.auth.currentUserOrNull() == null) {
            _state.update { it.copy(notice = "Revisa tu correo para confirmar la cuenta y después inicia sesión.") }
        } else load()
    }

    private suspend fun load() = snapshotMutex.withLock {
        val id = repository.userId()
        val profile = repository.ensureProfile()
        val connections = repository.connections()
        val profiles = repository.profiles()
        val messages = repository.quickMessages()
        val signals = repository.receivedSignals()
        val schedules = repository.accessibleSchedules()
        val statuses = repository.accessibleStatuses()
        if (repository.auth.currentUserOrNull()?.id != id) return@withLock
        if (baselineOwner != id) {
            baselineOwner = id; knownStatuses.clear()
        }
        val selected = _state.value.selectedPerson?.let { old -> profiles.find { it.id == old.id && it.id != profile.id } }
            ?: profiles.firstOrNull { it.id != profile.id }
        val newSignals = messageNotifications.update(id, signals, _state.value.settings.messages)
        allSchedules = schedules
        allStatuses = statuses
        _state.update { it.copy(signedIn = true, profile = profile,
            schedules = schedules.filter { it.userId == id }, statuses = statuses.filter { it.userId == id },
            connections = connections, profiles = profiles, messages = messages, signals = signals,
            selectedPerson = selected, personSchedules = schedules.filter { it.userId == selected?.id },
            personStatuses = statuses.filter { it.userId == selected?.id }, lastSync = Instant.now(), syncIssue = false) }
        if (_state.value.settings.messages) newSignals.forEach { signal ->
            val sender = profiles.find { it.id == signal.senderId }?.displayName ?: "Tu gente"
            NotificationHelper.showMessageNotification(getApplication(), signal.id, sender, signal.message)
        }
        updateStatusNotifications()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend fun observeWhileVisible() = coroutineScope {
        launch {
            while (isActive) {
                delay(15_000)
                val cutoff = Instant.now().minusSeconds(86400)
                _state.update { old -> old.copy(signals = old.signals.filter {
                    runCatching { Instant.parse(it.createdAt) > cutoff }.getOrDefault(false)
                }) }
                updateStatusNotifications()
            }
        }
        repository.auth.sessionStatus.collectLatest {
            val id = repository.auth.currentUserOrNull()?.id ?: return@collectLatest
            AccountLiveUpdates.observe(id).sample(500).collect {
                try { load() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { _state.update { old -> old.copy(syncIssue = true) } }
            }
        }
    }

    private fun updateStatusNotifications() {
        val state = _state.value
        val own = state.profile?.id ?: return
        if (repository.auth.currentUserOrNull()?.id != own) return
        val activeIds = state.profiles.map { it.id }.toSet()
        knownStatuses.keys.retainAll(activeIds)
        state.profiles.filter { it.id != own }.forEach { person ->
            val zone = runCatching { ZoneId.of(person.timezone) }.getOrDefault(ZoneId.systemDefault())
            val now = ZonedDateTime.now(zone)
            val status = StatusRepository.calculateStatus(person.displayName,
                allSchedules.filter { it.userId == person.id }, allStatuses.filter { it.userId == person.id },
                now.dayOfWeek.value, now, manualStatusFirst = true)
            val key = when (status) {
                is UserCurrentStatus.InClass -> "activity:${status.currentSchedule.id}"
                is UserCurrentStatus.Override -> "status:${status.customLocation}:${status.customMessage}"
                else -> "schedule"
            }
            val previous = knownStatuses.put(person.id, key)
            if (previous != null && previous != key) {
                when {
                    status is UserCurrentStatus.Override && state.settings.statuses ->
                        NotificationHelper.showStatusNotification(getApplication(), person.displayName, status.customLocation)
                    status is UserCurrentStatus.InClass && state.settings.activities ->
                        NotificationHelper.showStatusNotification(getApplication(), person.displayName, "Actividad en curso: ${status.currentSchedule.title}")
                    previous.startsWith("status:") && state.settings.statuses ->
                        NotificationHelper.showStatusNotification(getApplication(), person.displayName, "Vuelve a mostrar su horario.")
                }
            }
        }
    }

    fun openPeople() { _state.update { it.copy(peopleRevision = it.peopleRevision + 1) } }
    override fun setAlert(alert: AccountAlert, enabled: Boolean) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch { preferences.setAlert(id, alert, enabled) }
    }
    override fun setAccent(accent: AppAccent) { viewModelScope.launch { preferences.setAccent(accent) } }

    override fun refresh() = perform { load() }
    override fun register(name: String, email: String, username: String, password: String, confirmation: String) = perform {
        com.example.findme.domain.model.Registration.error(name, email, username, password, confirmation)?.let { throw IllegalArgumentException(it) }
        com.example.findme.data.repository.AuthorizedSignup.register(name, email, username, password)
        _state.update { it.copy(confirmationEmail = email.trim()) }
    }
    override fun resendConfirmation(email: String) = perform {
        require(com.example.findme.domain.model.Registration.isEmail(email)) { "Introduce un correo válido." }
        com.example.findme.data.repository.AuthorizedSignup.resend(email)
        _state.update { it.copy(confirmationEmail = email.trim(), notice = "Solicitud enviada. Revisa tu correo y la carpeta de spam.") }
    }
    override fun finishRegistration() {
        _state.update { it.copy(confirmationEmail = null, error = null, notice = null, loginRevision = it.loginRevision + 1) }
    }
    override fun saveProfile(name: String, username: String) = perform {
        val profile = repository.saveProfile(name, username)
        _state.update { old -> old.copy(profile = profile,
            profiles = old.profiles.filterNot { it.id == profile.id } + profile,
            notice = "Perfil guardado.") }
        com.example.findme.widget.ConnectionWidget().updateAll(getApplication())
    }
    override fun logout() = perform {
        repository.auth.signOut()
        _state.value = AccountState(initializing = false)
        com.example.findme.widget.ConnectionWidget().updateAll(getApplication())
    }
    override fun saveSchedule(existingId: String?, day: Int, start: String, end: String, title: String, location: String, category: String, modality: String) {
        val error = validateSchedule(day, start, end, title)
        if (error != null) { _state.update { it.copy(error = error) }; return }
        perform {
            repository.saveSchedule(ScheduleDto(existingId ?: UUID.randomUUID().toString(), repository.userId(), day,
                start.trim(), end.trim(), title.trim(), location.trim(), category, modality))
            load()
            _state.update { it.copy(notice = "Actividad guardada.") }
        }
    }
    override fun deleteSchedule(id: String) = perform { repository.deleteSchedule(id); load() }
    override fun setStatus(text: String, minutes: Long) = perform {
        repository.setStatus(text, minutes); load()
        _state.update { it.copy(notice = "Estado actualizado.") }
        com.example.findme.widget.ConnectionWidget().updateAll(getApplication())
    }
    override fun clearStatus() = perform {
        repository.clearStatus(); load()
        _state.update { it.copy(notice = "Se muestra tu horario de nuevo.") }
        com.example.findme.widget.ConnectionWidget().updateAll(getApplication())
    }
    override fun invite() = perform {
        val invitation = repository.createInvitation()
        _state.update { it.copy(invitation = invitation) }
    }
    override fun accept(code: String) = perform { repository.acceptInvitation(code); load(); _state.update { it.copy(notice = "Conexión establecida.") } }
    override fun disconnect(id: String) = perform { repository.disconnect(id); load() }
    override fun selectPerson(profile: AccountProfile) = perform {
        val schedules = repository.schedules(profile.id)
        val statuses = repository.overrides(profile.id)
        _state.update { it.copy(selectedPerson = profile, personSchedules = schedules, personStatuses = statuses) }
    }
    override fun saveMessage(label: String, message: String) = perform {
        repository.saveQuickMessage(label, message); load()
        _state.update { it.copy(notice = "Mensaje rápido creado.") }
    }
    override fun deleteMessage(id: String) = perform { repository.deleteQuickMessage(id); load() }
    override fun configureWidget(message: QuickMessage) {
        val person = _state.value.selectedPerson ?: return
        perform {
            com.example.findme.widget.ConnectionWidgetSettings.save(getApplication(), repository.userId(), person.id, message.id)
            _state.update { it.copy(notice = "Widget configurado para ${person.displayName}. Añade FindMe · Mi gente desde la pantalla de inicio de Android.") }
        }
    }
    override fun send(message: QuickMessage) {
        val recipient = _state.value.selectedPerson ?: return
        perform {
            repository.sendSignal(recipient.id, message.message)
            _state.update { it.copy(notice = "Mensaje enviado a ${recipient.displayName}. Se conserva durante 24 horas.") }
        }
    }
}
