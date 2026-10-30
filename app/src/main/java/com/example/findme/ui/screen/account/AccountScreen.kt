package com.example.findme.ui.screen.account

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.data.model.*
import com.example.findme.data.repository.StatusRepository
import com.example.findme.ui.components.HeroStatusCard
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

private val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

@Composable
fun AccountScreen(viewModel: AccountViewModel, onLegacy: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.initializing) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var tab by rememberSaveable(state.signedIn) { mutableStateOf("Mi día") }
    Scaffold(topBar = {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("FindMe", style = MaterialTheme.typography.headlineMedium)
            Text(state.profile?.let { "Hola, ${it.displayName}" } ?: "Conecta con tu gente", style = MaterialTheme.typography.bodyMedium)
        }
    }, bottomBar = {
        if (state.signedIn) NavigationBar {
            listOf("Mi día", "Horario", "Personas", "Cuenta").forEach { label ->
                NavigationBarItem(selected = tab == label, onClick = { tab = label },
                    icon = { Text(when (label) { "Mi día" -> "◉"; "Horario" -> "▦"; "Personas" -> "◎"; else -> "○" }) },
                    label = { Text(label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (!state.signedIn) LoginForm(state.busy, viewModel::login, onLegacy)
            else {
                OutlinedButton(onClick = viewModel::refresh, enabled = !state.busy) { Text("Actualizar") }
                when (tab) {
                    "Mi día" -> MyDay(state, viewModel)
                    "Horario" -> ScheduleManager(state, viewModel)
                    "Personas" -> People(state, viewModel)
                    else -> {
                        Text("Mi cuenta", style = MaterialTheme.typography.titleLarge)
                        Text(state.profile?.displayName.orEmpty())
                        Text("Zona horaria: ${state.profile?.timezone.orEmpty()}")
                        Text("Tú administras tus actividades. Las personas conectadas pueden consultar tu horario y estado; no pueden modificarlos.")
                        OutlinedButton(onClick = viewModel::logout, enabled = !state.busy) { Text("Cerrar sesión") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginForm(busy: Boolean, submit: (String, String, String, Boolean) -> Unit, onLegacy: () -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately do not enter saved instance state.
    var password by remember { mutableStateOf("") }
    Text(if (register) "Crea tu cuenta" else "Inicia sesión", style = MaterialTheme.typography.headlineSmall)
    Text("Organiza tus actividades y comparte tu día con las personas que elijas.")
    if (register) Field(name, { name = it.take(80) }, "Nombre visible")
    OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    if (register) Text("Usa al menos 8 caracteres. Podrías necesitar confirmar tu correo.")
    Button(onClick = { submit(email, password, name, register) }, enabled = !busy && email.isNotBlank() && password.isNotBlank() && (!register || name.isNotBlank())) {
        Text(if (register) "Crear cuenta" else "Entrar")
    }
    TextButton(onClick = { register = !register; password = "" }, enabled = !busy) { Text(if (register) "Ya tengo cuenta" else "Crear una cuenta") }
    HorizontalDivider()
    TextButton(onClick = onLegacy, enabled = !busy) { Text("Consultar vínculo de la versión anterior") }
}

@Composable
private fun StatusCard(profile: AccountProfile, schedules: List<ScheduleDto>, overrides: List<StatusOverrideDto>) {
    var now by remember(profile.timezone) { mutableStateOf(ZonedDateTime.now(ZoneId.of(profile.timezone))) }
    LaunchedEffect(profile.timezone) { while (true) { now = ZonedDateTime.now(ZoneId.of(profile.timezone)); delay(15_000) } }
    HeroStatusCard(StatusRepository.calculateStatus(profile.displayName, schedules, overrides, now.dayOfWeek.value, now))
    Text("Según su horario o estado compartido. No confirma su ubicación.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MyDay(state: AccountState, vm: AccountViewModel) {
    Text("Mi día", style = MaterialTheme.typography.headlineSmall)
    state.profile?.let { StatusCard(it, state.schedules, state.statuses) }
    var status by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf(60L) }
    Text("Compartir un estado temporal", style = MaterialTheme.typography.titleMedium)
    Text("Aparece cuando no tienes una actividad en curso.")
    Field(status, { status = it.take(120) }, "Tu estado")
    Choices(listOf(30L, 60L, 120L, 240L), minutes, { minutes = it }) { "$it min" }
    Button(onClick = { vm.setStatus(status, minutes) }, enabled = !state.busy && status.isNotBlank()) { Text("Publicar estado") }
    OutlinedButton(onClick = vm::clearStatus, enabled = !state.busy && state.statuses.isNotEmpty()) { Text("Quitar estado") }
    Text("Tus actividades de hoy", style = MaterialTheme.typography.titleMedium)
    val today = state.profile?.let { ZonedDateTime.now(ZoneId.of(it.timezone)).dayOfWeek.value }
    val schedules = state.schedules.filter { it.dayOfWeek == today }.sortedBy { it.startTime }
    if (schedules.isEmpty()) Text("No tienes actividades programadas para hoy.")
    schedules.forEach { ActivitySummary(it) }
}

@Composable
private fun ScheduleManager(state: AccountState, vm: AccountViewModel) {
    var editing by remember { mutableStateOf<ScheduleDto?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScheduleDto?>(null) }
    var day by rememberSaveable { mutableStateOf(1) }
    Text("Mi horario", style = MaterialTheme.typography.headlineSmall)
    Choices((1..7).toList(), day, { day = it }) { days[it - 1] }
    Button(onClick = { editing = null; showEditor = true }, enabled = !state.busy) { Text("Añadir actividad") }
    val schedules = state.schedules.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
    if (schedules.isEmpty()) Text("Este día está libre. Añade tu primera actividad.")
    schedules.forEach { schedule ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                ActivitySummary(schedule)
                Row {
                    TextButton(onClick = { editing = schedule; showEditor = true }, enabled = !state.busy) { Text("Editar") }
                    TextButton(onClick = { pendingDelete = schedule }, enabled = !state.busy) { Text("Eliminar") }
                }
            }
        }
    }
    if (showEditor) ScheduleEditor(editing, day, state.busy, state.error, state.notice,
        onDismiss = { showEditor = false }, onSave = vm::saveSchedule)
    pendingDelete?.let { schedule -> Confirm("¿Eliminar ${schedule.title}?", "Esta actividad se eliminará de tu horario.",
        onDismiss = { pendingDelete = null }, onConfirm = { vm.deleteSchedule(schedule.id); pendingDelete = null }) }
}

@Composable
private fun ScheduleEditor(existing: ScheduleDto?, initialDay: Int, busy: Boolean, error: String?, notice: String?, onDismiss: () -> Unit,
    onSave: (String?, Int, String, String, String, String, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(existing?.location.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(existing?.startTime?.take(5) ?: "09:00") }
    var end by rememberSaveable { mutableStateOf(existing?.endTime?.take(5) ?: "10:00") }
    var day by rememberSaveable { mutableStateOf(existing?.dayOfWeek ?: initialDay) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "university") }
    var modality by rememberSaveable { mutableStateOf(existing?.modality ?: "presencial") }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(busy, notice) { if (submitted && !busy && notice == "Actividad guardada.") onDismiss() }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (existing == null) "Nueva actividad" else "Editar actividad") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(title, { title = it.take(120) }, "Título")
                Choices((1..7).toList(), day, { day = it }) { days[it - 1] }
                Field(start, { start = it }, "Inicio (HH:mm)")
                Field(end, { end = it }, "Fin (HH:mm)")
                Field(location, { location = it.take(160) }, "Lugar (opcional)")
                Choices(listOf("university", "work", "free"), category, { category = it }) {
                    when (it) { "university" -> "Universidad"; "work" -> "Trabajo"; else -> "Libre" }
                }
                Choices(listOf("presencial", "virtual"), modality, { modality = it }) { it.replaceFirstChar(Char::titlecase) }
                if (submitted) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(onClick = { submitted = true; onSave(existing?.id, day, start, end, title, location, category, modality) }, enabled = !busy) { Text("Guardar") }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable
private fun People(state: AccountState, vm: AccountViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var disconnect by remember { mutableStateOf<PersonConnection?>(null) }
    val clipboard = LocalClipboardManager.current
    Text("Personas", style = MaterialTheme.typography.headlineSmall)
    Text("Conecta con tu pareja, familia o amigos. Al aceptar una invitación, ambos comparten su horario y estado hasta desvincularse.")
    Button(onClick = vm::invite, enabled = !state.busy) { Text("Generar invitación") }
    state.invitation?.let { invitation ->
        Text(invitation.code)
        Text("Válida durante 24 horas, para una sola persona. Generar otra invalida la anterior.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { clipboard.setText(AnnotatedString(invitation.code)) }) { Text("Copiar código") }
    }
    Field(code, { code = it.trim().take(32) }, "Código de invitación")
    OutlinedButton(onClick = { vm.accept(code) }, enabled = !state.busy && code.length == 32) { Text("Aceptar y compartir") }
    HorizontalDivider()
    if (state.connections.isEmpty()) Text("Aún no tienes personas conectadas.")
    state.connections.forEach { connection ->
        val person = state.profiles.find { it.id == connection.otherPerson(state.profile?.id.orEmpty()) }
        if (person != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.selectPerson(person) }, enabled = !state.busy) { Text(person.displayName) }
            TextButton(onClick = { disconnect = connection }, enabled = !state.busy) { Text("Desvincular") }
        }
    }
    state.selectedPerson?.let { person ->
        Text(person.displayName, style = MaterialTheme.typography.titleLarge)
        StatusCard(person, state.personSchedules, state.personStatuses)
        Text("Mensajes rápidos", style = MaterialTheme.typography.titleMedium)
        if (state.messages.isEmpty()) Text("Crea tus propios botones abajo para enviar mensajes a esta persona.")
        state.messages.forEach { quick ->
            Column {
            Row {
                Button(onClick = { vm.send(quick) }, enabled = !state.busy) { Text(quick.label) }
                TextButton(onClick = { vm.deleteMessage(quick.id) }, enabled = !state.busy) { Text("Quitar") }
            }
            TextButton(onClick = { vm.configureWidget(quick) }, enabled = !state.busy) { Text("Usar este botón en el widget") }
            }
        }
        Text("Su horario semanal", style = MaterialTheme.typography.titleMedium)
        state.personSchedules.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime })).forEach { ActivitySummary(it, true) }
    }
    HorizontalDivider()
    Text("Configura un botón", style = MaterialTheme.typography.titleMedium)
    Field(label, { label = it.take(30) }, "Nombre del botón")
    Field(message, { message = it.take(240) }, "Mensaje que enviará")
    Button(onClick = { vm.saveMessage(label, message) }, enabled = !state.busy && label.isNotBlank() && message.isNotBlank()) { Text("Guardar botón") }
    Text("Mensajes recibidos", style = MaterialTheme.typography.titleMedium)
    Text("Pulsa Actualizar para consultar mensajes nuevos.", style = MaterialTheme.typography.bodySmall)
    if (state.signals.isEmpty()) Text("No tienes mensajes recibidos.")
    state.signals.forEach { signal ->
        val sender = state.profiles.find { it.id == signal.senderId }?.displayName ?: "Persona conectada"
        Text("$sender: ${signal.message}")
    }
    disconnect?.let { connection -> Confirm("¿Desvincular persona?", "Ambos dejarán de tener acceso al horario y estado del otro.",
        onDismiss = { disconnect = null }, onConfirm = { vm.disconnect(connection.id); disconnect = null }) }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun <T> Choices(values: List<T>, selected: T, change: (T) -> Unit, label: (T) -> String) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value -> FilterChip(selected = selected == value, onClick = { change(value) }, label = { Text(label(value)) }) }
    }
}

@Composable
private fun ActivitySummary(schedule: ScheduleDto, showDay: Boolean = false) {
    Text((if (showDay) "${days[schedule.dayOfWeek - 1]} · " else "") + "${schedule.startTime.take(5)}–${schedule.endTime.take(5)} · ${schedule.title}",
        style = MaterialTheme.typography.bodyLarge)
    if (schedule.location.isNotBlank()) Text(schedule.location, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Confirm(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
