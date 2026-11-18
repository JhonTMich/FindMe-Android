package com.example.findme.ui.screen.account

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.model.*
import com.example.findme.data.repository.AccountRepository
import com.example.findme.domain.model.validateSchedule
import com.example.findme.domain.model.conflictMessage
import com.example.findme.domain.model.findScheduleConflict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
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
import com.example.findme.domain.model.ScheduleSyncGuard
import com.example.findme.domain.model.ConnectionControls
import com.example.findme.util.NotificationHelper
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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
    /** Signed in through a recovery link; the new password has not been chosen yet. */
    val passwordRecovery: Boolean = false,
    val loginRevision: Int = 0,
    val profile: AccountProfile? = null,
    val schedules: List<ScheduleDto> = emptyList(),
    val savedScheduleDraftId: String? = null,
    val statuses: List<StatusOverrideDto> = emptyList(),
    val connections: List<PersonConnection> = emptyList(),
    val profiles: List<AccountProfile> = emptyList(),
    val selectedPerson: AccountProfile? = null,
    val personSchedules: List<ScheduleDto> = emptyList(),
    val personStatuses: List<StatusOverrideDto> = emptyList(),
    val messages: List<QuickMessage> = emptyList(),
    val signals: List<ConnectionSignal> = emptyList(),
    val invitation: ConnectionInvitation? = null,
    val preferences: List<ConnectionPreference> = emptyList()
) {
    /** Nicknames, sharing choices and silenced people, as decided by the signed-in person. */
    val controls: ConnectionControls get() = ConnectionControls(profile?.id.orEmpty(), connections, preferences)
}

private const val SAVE_FAILED = "No se pudieron guardar los datos. Inténtalo de nuevo."
private const val LOAD_FAILED = "No se pudieron cargar tus datos. Inténtalo de nuevo en unos minutos."

class AccountViewModel(application: Application) : AndroidViewModel(application), AccountActions {
    private val repository = AccountRepository()
    private val preferences = AccountSettingsRepository(application)
    private val snapshotMutex = Mutex()
    private val scheduleSync = ScheduleSyncGuard()
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
                if (user == null) {
                    _state.value = AccountState(initializing = false)
                    com.example.findme.widget.WidgetStore.clear(getApplication())
                    AccountCache.clear(getApplication())
                }
                else if (_state.value.profile?.id != user.id) {
                    _state.update { old -> old.copy(initializing = false, signedIn = true) }
                    // Show the last successful sync at once, then refresh it from the network.
                    restoreCache(user.id)
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

    /** [failure] is shown for unexpected server errors; reads must not claim that something failed to save. */
    private fun perform(quiet: Boolean = false, failure: String = SAVE_FAILED, action: suspend () -> Unit) {
        if (_state.value.busy) {
            // Say why nothing happened instead of dropping the tap silently. A refresh is simply skipped.
            if (!quiet) _state.update { it.copy(notice = "Espera a que termine la operación en curso e inténtalo de nuevo.") }
            return
        }
        _state.update { it.copy(busy = true, error = null, notice = null, savedScheduleDraftId = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: IllegalArgumentException) {
                _state.update { it.copy(error = error.message ?: "Revisa los datos ingresados.") }
            }
            catch (error: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
                val message = when (error.code) {
                    "23505" -> "Ese nombre de usuario ya está ocupado. Elige otro."
                    "23P01" -> "Este horario coincide con otra actividad. Actualiza tu horario y elige otra hora."
                    "FM001" -> "Llegaste al máximo permitido. Elimina elementos que ya no uses e inténtalo de nuevo."
                    "FM002" -> "Demasiados intentos seguidos. Espera unos minutos e inténtalo de nuevo."
                    "FM004" -> "La fecha o las horas de esta actividad no son válidas. Revisa la ocurrencia."
                    "FM003" -> "La zona horaria del teléfono no es válida. Revisa la configuración de fecha y hora."
                    else -> failure
                }
                // Only the code: the full error carries the request URL with account ids.
                if (message == failure) android.util.Log.w("AccountViewModel", "Server rejected the request (code ${error.code})")
                _state.update { it.copy(error = message) }
            }
            catch (error: Exception) {
                android.util.Log.w("AccountViewModel", "Account operation failed", error)
                _state.update { it.copy(error = "No se pudo completar la operación. Comprueba tu conexión, tus datos y la configuración de cuentas del servidor.") }
            } finally { _state.update { it.copy(busy = false, initializing = false) } }
        }
    }

    override fun requestPasswordReset(email: String) = perform {
        require(com.example.findme.domain.model.Registration.isEmail(email)) { "Introduce un correo válido." }
        repository.requestPasswordReset(email)
        _state.update { it.copy(notice = "Si ese correo tiene una cuenta, recibirás un enlace para elegir otra contraseña. " +
            "Ábrelo en este teléfono y revisa también la carpeta de spam.") }
    }
    /** Called when the recovery email's link opens the app. */
    fun completePasswordRecovery(code: String) = perform(failure = LOAD_FAILED) {
        repository.exchangeRecoveryCode(code)
        _state.update { it.copy(signedIn = true, passwordRecovery = true) }
        // The session listener's refresh is skipped while this action is running, so load here.
        load()
    }
    fun recoveryLinkFailed() {
        _state.update { it.copy(error = "El enlace para cambiar la contraseña no es válido o venció. Solicita uno nuevo.") }
    }
    override fun setRecoveredPassword(password: String, confirmation: String) = perform {
        require(password == confirmation) { "Las contraseñas no coinciden." }
        repository.setPassword(password)
        _state.update { it.copy(passwordRecovery = false, notice = "Contraseña actualizada. Ya puedes usarla para entrar.") }
    }
    override fun cancelPasswordRecovery() { _state.update { it.copy(passwordRecovery = false) } }
    override fun login(username: String, password: String) = perform(failure = LOAD_FAILED) {
        require(com.example.findme.domain.model.Username.isValid(username) && password.isNotBlank()) { "Introduce un usuario válido y tu contraseña." }
        repository.signIn(username, password)
        if (repository.auth.currentUserOrNull() == null) {
            _state.update { it.copy(notice = "Revisa tu correo para confirmar la cuenta y después inicia sesión.") }
        } else load()
    }

    private suspend fun <T> readSection(section: String, read: suspend () -> T): T? = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val code = (error as? io.github.jan.supabase.postgrest.exception.PostgrestRestException)?.code
            ?: error.javaClass.simpleName
        android.util.Log.w("AccountViewModel", "Unable to synchronize $section ($code)")
        null
    }

    private suspend fun load() = snapshotMutex.withLock {
        coroutineScope {
            val id = repository.userId()
            val scheduleRevision = scheduleSync.snapshotRevision()
            val profileRequest = async { readSection("profile", repository::ensureProfile) }
            val connectionsRequest = async { readSection("connections", repository::connections) }
            val profilesRequest = async { readSection("people", repository::profiles) }
            val messagesRequest = async { readSection("quick messages", repository::quickMessages) }
            val signalsRequest = async { readSection("inbox", repository::receivedSignals) }
            val schedulesRequest = async { readSection("schedules", repository::accessibleSchedules) }
            val statusesRequest = async { readSection("statuses", repository::accessibleStatuses) }
            val preferencesRequest = async { readSection("connection preferences", repository::connectionPreferences) }
            val remoteProfile = profileRequest.await()
            val remoteConnections = connectionsRequest.await()
            val remoteProfiles = profilesRequest.await()
            val remoteMessages = messagesRequest.await()
            val remoteSignals = signalsRequest.await()
            val remoteSchedules = schedulesRequest.await()
            val remoteStatuses = statusesRequest.await()
            val remotePreferences = preferencesRequest.await()
            if (repository.auth.currentUserOrNull()?.id != id) return@coroutineScope

            val old = _state.value
            val previous = if (old.profile?.id == id) old else null
            val profile = remoteProfile ?: previous?.profile ?: run {
                _state.update { it.copy(syncIssue = true) }
                return@coroutineScope
            }
            val connections = remoteConnections ?: previous?.connections.orEmpty()
            val peopleIds = connections.map { it.otherPerson(id) }.toSet()
            val profiles = (remoteProfiles ?: previous?.profiles.orEmpty()).filter { it.id in peopleIds } + profile
            val visibleIds = profiles.mapTo(mutableSetOf()) { it.id }
            val messages = remoteMessages ?: previous?.messages.orEmpty()
            val signals = remoteSignals ?: previous?.signals.orEmpty()
            val preferences = (remotePreferences ?: previous?.preferences.orEmpty())
                .filter { preference -> connections.any { it.id == preference.connectionId } }
            val complete = listOf(remoteProfile, remoteConnections, remoteProfiles, remoteMessages,
                remoteSignals, remoteSchedules, remoteStatuses, remotePreferences).all { it != null }
            val previousSchedules = if (previous == null) emptyList() else allSchedules
            val previousStatuses = if (previous == null) emptyList() else allStatuses
            // An older response must not undo a confirmed save or resurrect a deleted row.
            val ownSchedules = if (remoteSchedules != null && scheduleSync.canApplySnapshot(scheduleRevision)) {
                remoteSchedules.filter { it.userId == id }
            } else previous?.schedules.orEmpty()
            val schedules = (remoteSchedules ?: previousSchedules)
                .filter { it.userId != id && it.userId in visibleIds } + ownSchedules
            val statuses = (remoteStatuses ?: previousStatuses).filter { it.userId in visibleIds }
            if (baselineOwner != id) {
                baselineOwner = id
                knownStatuses.clear()
            }
            val selected = previous?.selectedPerson?.let { person -> profiles.find { it.id == person.id && it.id != id } }
                ?: profiles.firstOrNull { it.id != id }
            val syncedAt = if (complete) Instant.now() else previous?.lastSync
            allSchedules = schedules
            allStatuses = statuses
            _state.update { it.copy(signedIn = true, profile = profile,
                schedules = ownSchedules, statuses = statuses.filter { it.userId == id },
                connections = connections, profiles = profiles, messages = messages, signals = signals,
                preferences = preferences, selectedPerson = selected, personSchedules = schedules.filter { it.userId == selected?.id },
                personStatuses = statuses.filter { it.userId == selected?.id }, lastSync = syncedAt, syncIssue = !complete) }
            val current = _state.value
            if (remoteSignals != null && remotePreferences != null && remoteProfiles != null && remoteConnections != null) {
                val newSignals = messageNotifications.update(id, signals, current.settings.messages)
                current.controls.notifiable(newSignals).forEach { signal ->
                    val sender = profiles.find { it.id == signal.senderId }?.let(current.controls::nameOf) ?: "Tu gente"
                    NotificationHelper.showMessageNotification(getApplication(), signal.id, sender, signal.message)
                }
            }
            if (complete || previous != null) saveCurrentCache()
            if (remoteSchedules != null && remoteStatuses != null && remoteConnections != null && remoteProfiles != null) {
                publishWidgetSnapshot()
            }
            if (remoteSchedules != null && remoteStatuses != null && remotePreferences != null) updateStatusNotifications()
        }
    }

    private suspend fun refreshAfterSave(notice: String? = null) {
        val owner = repository.auth.currentUserOrNull()?.id ?: return
        if (notice != null) _state.update { it.copy(notice = notice) }
        if (readSection("after saving") { load() } == null && repository.auth.currentUserOrNull()?.id == owner) {
            _state.update { it.copy(syncIssue = true) }
        }
    }

    private suspend fun saveCurrentCache() {
        val current = _state.value
        val profile = current.profile ?: return
        saveCache(AccountSnapshot(profile.id, profile, current.profiles, allSchedules, allStatuses,
            current.connections, current.messages, current.signals, current.lastSync?.toString().orEmpty(),
            current.preferences))
    }

    private suspend fun saveCache(snapshot: AccountSnapshot) {
        try {
            if (repository.auth.currentUserOrNull()?.id == snapshot.owner) AccountCache.save(getApplication(), snapshot)
            // A logout that happened during the write must not leave this account's data behind.
            if (repository.auth.currentUserOrNull()?.id != snapshot.owner) AccountCache.clear(getApplication())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("AccountViewModel", "Account cache update failed", error) }
    }

    private suspend fun restoreCache(id: String) {
        val cached = try { AccountCache.load(getApplication(), id) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null } ?: return
        if (repository.auth.currentUserOrNull()?.id != id || _state.value.profile != null) return
        val cutoff = Instant.now().minusSeconds(86400)
        val signals = cached.signals.filter { runCatching { Instant.parse(it.createdAt) > cutoff }.getOrDefault(false) }
        // Messages already seen before closing the app are not notified again.
        messageNotifications.update(id, signals, enabled = false)
        val selected = cached.profiles.firstOrNull { it.id != id }
        allSchedules = cached.schedules
        allStatuses = cached.statuses
        _state.update { it.copy(signedIn = true, initializing = false, profile = cached.profile,
            schedules = cached.schedules.filter { row -> row.userId == id }, statuses = cached.statuses.filter { row -> row.userId == id },
            connections = cached.connections, profiles = cached.profiles, messages = cached.messages, signals = signals,
            preferences = cached.preferences,
            selectedPerson = selected, personSchedules = cached.schedules.filter { row -> row.userId == selected?.id },
            personStatuses = cached.statuses.filter { row -> row.userId == selected?.id },
            lastSync = runCatching { Instant.parse(cached.syncedAt) }.getOrNull()) }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend fun observeWhileVisible(): Unit = coroutineScope {
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

    private suspend fun publishWidgetSnapshot() {
        val profile = _state.value.profile ?: return
        if (repository.auth.currentUserOrNull()?.id != profile.id) return
        try {
            com.example.findme.widget.WidgetStore.publish(getApplication(), profile.id,
                _state.value.profiles.filterNot { it.id == profile.id } + profile, allSchedules, allStatuses) {
                repository.auth.currentUserOrNull()?.id == profile.id
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("AccountViewModel", "Widget update failed", error) }
    }

    private fun updateStatusNotifications() {
        val state = _state.value
        val own = state.profile?.id ?: return
        if (repository.auth.currentUserOrNull()?.id != own) return
        val activeIds = state.profiles.map { it.id }.toSet()
        knownStatuses.keys.retainAll(activeIds)
        val controls = state.controls
        state.profiles.filter { it.id != own }.forEach { person ->
            val zone = runCatching { ZoneId.of(person.timezone) }.getOrDefault(ZoneId.systemDefault())
            val now = ZonedDateTime.now(zone)
            val status = StatusRepository.calculateStatus(person.displayName,
                allSchedules.filter { it.userId == person.id }, allStatuses.filter { it.userId == person.id },
                now.dayOfWeek.value, now)
            val key = when (status) {
                is UserCurrentStatus.InClass -> "activity:${status.currentSchedule.id}"
                is UserCurrentStatus.Override -> "status:${status.customLocation}:${status.customMessage}"
                else -> "schedule"
            }
            val previous = knownStatuses.put(person.id, key)
            // A silenced person's changes are still tracked, so unsilencing does not replay them.
            if (previous != null && previous != key && !controls.isMuted(person.id)) {
                val name = controls.nameOf(person)
                when {
                    status is UserCurrentStatus.Override && state.settings.statuses ->
                        NotificationHelper.showStatusNotification(getApplication(), name, status.customLocation)
                    status is UserCurrentStatus.InClass && state.settings.activities ->
                        NotificationHelper.showStatusNotification(getApplication(), name, "Actividad en curso: ${status.currentSchedule.title}")
                    previous.startsWith("status:") && state.settings.statuses ->
                        NotificationHelper.showStatusNotification(getApplication(), name, "Vuelve a mostrar su horario.")
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

    override fun refresh() = perform(quiet = true, failure = LOAD_FAILED) { load() }
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
        publishWidgetSnapshot()
    }
    override fun logout() = perform {
        repository.auth.signOut()
        _state.value = AccountState(initializing = false)
        com.example.findme.widget.WidgetStore.clear(getApplication())
    }
    override fun saveSchedules(draftId: String, activities: List<ScheduleDto>) {
        perform {
            require(activities.isNotEmpty()) { "Elige al menos un día." }
            val currentSchedules = _state.value.schedules
            activities.forEach { activity ->
                val error = validateSchedule(activity.dayOfWeek, activity.startTime, activity.endTime, activity.title, activity.endsNextDay)
                    ?: findScheduleConflict(currentSchedules, activity)?.conflictMessage()
                if (error != null) throw IllegalArgumentException(error)
            }

            val ownerId = repository.userId()
            scheduleSync.beginWrite()
            try {
                val rows = repository.saveSchedules(activities.map { it.copy(userId = ownerId) })
                if (repository.auth.currentUserOrNull()?.id != ownerId) return@perform

                val ids = rows.map { it.id }.toSet()
                allSchedules = allSchedules.filterNot { it.id in ids } + rows
                _state.update { old -> old.copy(
                    schedules = old.schedules.filterNot { it.id in ids } + rows,
                    savedScheduleDraftId = draftId,
                    notice = if (rows.size == 1) "Actividad guardada." else "Actividad guardada en ${rows.size} días."
                ) }
            } finally { scheduleSync.finishWrite() }
            snapshotMutex.withLock { saveCurrentCache() }
            publishWidgetSnapshot()
        }
    }
    override fun changeScheduleOccurrence(draftId: String, id: String, originalDate: String, change: ScheduleException?) = perform {
        val ownerId = repository.userId()
        scheduleSync.beginWrite()
        try {
            val updated = repository.changeScheduleOccurrence(id, originalDate, change)
            if (repository.auth.currentUserOrNull()?.id != ownerId) return@perform
            allSchedules = allSchedules.map { if (it.id == id) updated else it }
            _state.update { old -> old.copy(
                schedules = old.schedules.map { if (it.id == id) updated else it },
                savedScheduleDraftId = draftId,
                notice = when {
                    change == null -> "Actividad semanal restaurada."
                    change.cancelled -> "Actividad cancelada solo para esa fecha."
                    else -> "Actividad reprogramada solo para esa fecha."
                }
            ) }
        } finally {
            scheduleSync.finishWrite()
        }
        snapshotMutex.withLock { saveCurrentCache() }
        publishWidgetSnapshot()
    }

    override fun deleteSchedule(id: String) = perform {
        val ownerId = repository.userId()
        scheduleSync.beginWrite()
        try {
            repository.deleteSchedule(id)
            if (repository.auth.currentUserOrNull()?.id != ownerId) return@perform
            allSchedules = allSchedules.filterNot { it.id == id }
            _state.update { old -> old.copy(
                schedules = old.schedules.filterNot { it.id == id },
                notice = "Actividad eliminada."
            ) }
        } finally { scheduleSync.finishWrite() }
        snapshotMutex.withLock { saveCurrentCache() }
        publishWidgetSnapshot()
    }
    override fun setStatus(text: String, minutes: Long) = perform {
        repository.setStatus(text, minutes)
        refreshAfterSave("Estado actualizado.")
    }
    override fun clearStatus() = perform {
        repository.clearStatus()
        refreshAfterSave("Se muestra tu horario de nuevo.")
    }
    override fun invite() = perform {
        val invitation = repository.createInvitation()
        _state.update { it.copy(invitation = invitation) }
    }
    override fun accept(code: String) = perform {
        repository.acceptInvitation(code)
        refreshAfterSave("Conexión establecida.")
    }
    override fun disconnect(id: String) = perform {
        val connection = _state.value.connections.find { it.id == id }
        val owner = repository.userId()
        repository.disconnect(id)
        if (repository.auth.currentUserOrNull()?.id != owner) return@perform
        val removed = connection?.otherPerson(owner)
        if (removed != null) {
            allSchedules = allSchedules.filterNot { it.userId == removed }
            allStatuses = allStatuses.filterNot { it.userId == removed }
            _state.update { old ->
                val wasSelected = old.selectedPerson?.id == removed
                old.copy(connections = old.connections.filterNot { it.id == id },
                    profiles = old.profiles.filterNot { it.id == removed },
                    preferences = old.preferences.filterNot { it.connectionId == id },
                    selectedPerson = if (wasSelected) null else old.selectedPerson,
                    personSchedules = if (wasSelected) emptyList() else old.personSchedules,
                    personStatuses = if (wasSelected) emptyList() else old.personStatuses)
            }
            readSection("local cache after disconnecting") { AccountCache.clear(getApplication()) }
            publishWidgetSnapshot()
        }
        refreshAfterSave("Conexión eliminada.")
    }
    override fun selectPerson(profile: AccountProfile) = perform {
        val schedules = repository.schedules(profile.id)
        val statuses = repository.overrides(profile.id)
        _state.update { it.copy(selectedPerson = profile, personSchedules = schedules, personStatuses = statuses) }
    }
    override fun saveMessage(label: String, message: String) = perform {
        repository.saveQuickMessage(label, message)
        refreshAfterSave("Mensaje rápido creado.")
    }
    override fun deleteMessage(id: String) = perform {
        repository.deleteQuickMessage(id)
        _state.update { old -> old.copy(messages = old.messages.filterNot { it.id == id }) }
        refreshAfterSave("Mensaje rápido eliminado.")
    }
    override fun saveConnectionPreference(preference: ConnectionPreference) = perform {
        repository.saveConnectionPreference(preference)
        _state.update { old -> old.copy(preferences = old.preferences
            .filterNot { it.connectionId == preference.connectionId } + preference) }
        refreshAfterSave("Preferencias guardadas.")
    }
    override fun markSignalRead(id: String) = perform(quiet = true) {
        repository.markSignalRead(id)
        val now = Instant.now().toString()
        _state.update { old -> old.copy(signals = old.signals.map { if (it.id == id && it.readAt == null) it.copy(readAt = now) else it }) }
    }
    override fun deleteSignal(id: String) = perform {
        repository.deleteSignal(id)
        _state.update { old -> old.copy(signals = old.signals.filterNot { it.id == id }, notice = "Mensaje borrado.") }
    }
    override fun changePassword(current: String, new: String, confirmation: String) = perform {
        require(new == confirmation) { "Las contraseñas no coinciden." }
        val username = requireNotNull(_state.value.profile?.username) { "Elige un nombre de usuario antes de cambiar la contraseña." }
        repository.changePassword(username, current, new)
        _state.update { it.copy(notice = "Contraseña actualizada.") }
    }
    override fun deleteAccount(password: String) = perform {
        repository.deleteAccount(password)
        _state.value = AccountState(initializing = false, notice = "Tu cuenta y tus datos se eliminaron.")
        com.example.findme.widget.WidgetStore.clear(getApplication())
    }
    override fun exportData(): String? {
        val state = _state.value
        val profile = state.profile ?: return null
        return com.example.findme.domain.model.AccountExport.build(profile, state.schedules, state.statuses, state.messages,
            state.connections, state.profiles, state.signals, Instant.now().toString())
    }
    override fun exportFinished(saved: Boolean) {
        _state.update { it.copy(notice = if (saved) "Copia de tus datos guardada." else null,
            error = if (saved) null else "No se pudo guardar la copia de tus datos.") }
    }
    override fun send(message: QuickMessage) {
        val recipient = _state.value.selectedPerson ?: return
        perform {
            repository.sendSignal(recipient.id, message.message)
            _state.update { it.copy(notice = "Mensaje enviado a ${_state.value.controls.nameOf(recipient)}. Se conserva durante 24 horas.") }
        }
    }
}
