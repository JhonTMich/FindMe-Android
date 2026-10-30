@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.findme.ui.screen.account

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.data.model.*
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.Username
import com.example.findme.domain.model.InvitationCode
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.ui.components.*
import com.example.findme.ui.theme.*
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val cyan = Color(0xFF67D8E8)
private fun AccountProfile.zone() = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())

@Composable
fun AccountScreen(viewModel: AccountViewModel, onLegacy: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountContent(state, viewModel, onLegacy)
}

@Composable
fun AccountContent(state: AccountState, actions: AccountActions, onLegacy: () -> Unit = {}) {
    if (state.initializing) {
        Box(Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = IndigoPrimary) }
        return
    }
    var tab by rememberSaveable(state.signedIn) { mutableStateOf("Mi día") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it) } }
    Scaffold(containerColor = DarkBackground, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Color(0xFF7370ED), Color(0xFF427FAD))), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.NearMe, null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("FindMe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(state.profile?.takeUnless { it.needsName }?.let { "Hola, ${it.displayName}" } ?: "Siempre cerca de tu gente", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (state.signedIn) {
                IconButton(onClick = actions::refresh, enabled = !state.busy) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), color = IndigoPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Actualizar", tint = TextSecondary)
                }
            }
        }
    }, bottomBar = {
        if (state.signedIn && state.profile?.needsProfile == false) NavigationBar(containerColor = DarkSurface) {
            listOf(Triple("Mi día", "Hoy", Icons.Default.Today), Triple("Horario", "Horario", Icons.Default.CalendarMonth),
                Triple("Personas", "Personas", Icons.Default.PeopleOutline), Triple("Cuenta", "Cuenta", Icons.Default.PersonOutline)).forEach { (id, title, icon) ->
                NavigationBarItem(selected = tab == id, onClick = { tab = id }, icon = { Icon(icon, null) }, label = { Text(title) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = IndigoPrimary, selectedTextColor = IndigoPrimary,
                        indicatorColor = IndigoCardBg, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))
            }
        }
    }) { padding ->
        Crossfade(tab, modifier = Modifier.fillMaxSize().padding(padding), label = "accountNavigation") { activeTab ->
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10172A), DarkBackground), endY = 1000f))
                .verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                state.error?.let { error -> Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } }
                if (!state.signedIn) LoginForm(state.busy, actions::login, onLegacy)
                else if (state.profile == null) {
                    Panel {
                        Text("Cargando tu perfil…", color = TextSecondary)
                        OutlinedButton(onClick = actions::logout, enabled = !state.busy) { Text("Cerrar sesión") }
                    }
                }
                else if (state.profile.needsProfile) CompleteProfile(state, actions)
                else when (activeTab) {
                    "Mi día" -> MyDay(state, actions)
                    "Horario" -> ScheduleManager(state, actions)
                    "Personas" -> People(state, actions)
                    else -> AccountProfilePanel(state, actions)
                }
            }
        }
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(eyebrow.uppercase(), color = IndigoPrimary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, DarkCardBorder),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun SectionHeading(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentIcon(icon, IndigoPrimary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LoginForm(busy: Boolean, submit: (String, String) -> Unit, onLegacy: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    PageHeading("Tu día, compartido", "Qué bueno verte.",
        "Organiza tus actividades y mantente conectado con quienes tú elijas.")
    Panel {
        SectionHeading(Icons.Default.PersonOutline, "Inicia sesión")
        Field(username, { username = it.take(24) }, "Nombre de usuario", keyboard = KeyboardOptions(autoCorrectEnabled = false))
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Button(onClick = { submit(username, password) }, modifier = Modifier.fillMaxWidth(), enabled = !busy && Username.isValid(username) && password.isNotBlank()) {
            Text("Entrar")
        }
        Text("El acceso es por invitación. Para obtener una cuenta, contacta al responsable de FindMe.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = onLegacy, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Consultar vínculo anterior", color = TextMuted) }
}

@Composable
private fun CompleteProfile(state: AccountState, actions: AccountActions) {
    PageHeading("Un gusto conocerte", "Completa tu perfil", "Elige el nombre que verá tu gente cuando conecten contigo.")
    Panel {
        SectionHeading(Icons.Default.PersonOutline, "¿Cómo te llamas?")
        ProfileNameForm(state, actions, completing = true)
        TextButton(onClick = actions::logout, enabled = !state.busy) { Text("Cerrar sesión") }
    }
}

@Composable
private fun ProfileNameForm(state: AccountState, actions: AccountActions, completing: Boolean = false) {
    val profile = state.profile ?: return
    var name by rememberSaveable(profile.id, profile.displayName) { mutableStateOf(if (profile.needsName) "" else profile.displayName) }
    var username by rememberSaveable(profile.id, profile.username) { mutableStateOf(profile.username.orEmpty()) }
    val normalized = Username.normalize(username)
    val trimmed = name.trim()
    val valid = trimmed.length in 1..80 && !trimmed.equals("Mi cuenta", ignoreCase = true) && Username.isValid(username)
    Field(name, { name = it.take(80) }, "Nombre visible", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    Field(username, { username = it.take(24) }, "Nombre de usuario", keyboard = KeyboardOptions(autoCorrectEnabled = false))
    Text("Para entrar: de 3 a 24 letras sin tildes, números o guion bajo. No distingue mayúsculas.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
    Button(onClick = { actions.saveProfile(trimmed, normalized) }, modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy && valid && (completing || trimmed != profile.displayName || normalized != profile.username)) {
        Text(if (state.busy) "Guardando…" else if (completing) "Guardar y continuar" else "Guardar perfil")
    }
}

@Composable
private fun sharedStatus(profile: AccountProfile, schedules: List<ScheduleDto>, overrides: List<StatusOverrideDto>): UserCurrentStatus {
    val zone = profile.zone()
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) { while (true) { now = ZonedDateTime.now(zone); delay(15_000) } }
    return StatusRepository.calculateStatus(profile.displayName, schedules, overrides, now.dayOfWeek.value, now, manualStatusFirst = true)
}

@Composable
private fun MyDay(state: AccountState, actions: AccountActions) {
    var editStatus by rememberSaveable { mutableStateOf(false) }
    val profile = state.profile
    val status = profile?.let { sharedStatus(it, state.schedules, state.statuses) }
    val now = ZonedDateTime.now(profile?.zone() ?: ZoneId.systemDefault())
    PageHeading("Siempre cerca", "Mi día", now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))))
    status?.let { HeroStatusCard(it) }
    Panel {
        SectionHeading(Icons.Default.EditLocationAlt, "¿Cambiaste de planes?", "Comparte lo que estás haciendo ahora.")
        Text("Tu estado tiene prioridad sobre el horario hasta que termine o lo quites.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { editStatus = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
            Icon(Icons.Default.Edit, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
            Text(if (status is UserCurrentStatus.Override) "Cambiar mi estado" else "Compartir mi estado")
        }
        if (status is UserCurrentStatus.Override) TextButton(onClick = actions::clearStatus, enabled = !state.busy) { Text("Volver al horario", color = TextSecondary) }
    }
    val today = state.schedules.filter { it.dayOfWeek == now.dayOfWeek.value }.sortedBy { it.startTime }
    SectionHeading(Icons.Default.CalendarToday, "Tu agenda de hoy", "${today.size} actividades programadas")
    if (today.isEmpty()) EmptyPanel(Icons.Default.WbSunny, "Un día a tu ritmo", "Aún no tienes actividades para hoy. Puedes añadirlas en Horario.")
    today.forEachIndexed { index, schedule -> ActivityTimelineCard(schedule,
        active = (status as? UserCurrentStatus.InClass)?.currentSchedule?.id == schedule.id, last = index == today.lastIndex) }
    if (editStatus) StatusEditor(state, actions, status as? UserCurrentStatus.Override) { editStatus = false }
}

@Composable
private fun StatusEditor(state: AccountState, actions: AccountActions, current: UserCurrentStatus.Override?, dismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.customLocation.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(60L) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.notice, state.busy) {
        if (submitted && !state.busy && state.notice in listOf("Estado actualizado.", "Se muestra tu horario de nuevo.")) dismiss()
    }
    Sheet(dismiss, "Tu estado, ahora", "Lo verán las personas conectadas contigo.") {
        Choices(listOf("En camino a casa", "En casa", "Ocupado", "Disponible"), text, { text = it }) { it }
        Field(text, { text = it.take(120) }, "¿Qué estás haciendo?", placeholder = "Escribe tu propio estado")
        Text("¿Durante cuánto tiempo?", style = MaterialTheme.typography.titleSmall)
        Choices(listOf(30L, 60L, 120L, 240L), minutes, { minutes = it }) { if (it < 60) "$it min" else "${it / 60} h" }
        Text("Al terminar se mostrará de nuevo tu horario. Puedes cambiarlo cuando quieras.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        if (submitted) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submitted = true; actions.setStatus(text, minutes) }, enabled = !state.busy && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Compartir estado") }
        if (current != null) TextButton(onClick = { submitted = true; actions.clearStatus() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Quitar estado y volver al horario") }
    }
}

@Composable
private fun ScheduleManager(state: AccountState, actions: AccountActions) {
    var editing by remember { mutableStateOf<ScheduleDto?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScheduleDto?>(null) }
    var day by rememberSaveable { mutableStateOf(ZonedDateTime.now(state.profile?.zone() ?: ZoneId.systemDefault()).dayOfWeek.value) }
    WeeklyScheduleHeader(state.schedules, day, onDaySelected = { day = it }, showDayTitle = false)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(days[day - 1], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = { editing = null; showEditor = true }, enabled = !state.busy,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = IndigoCardBg, contentColor = IndigoPrimary)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Añadir")
        }
    }
    val schedules = state.schedules.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
    if (schedules.isEmpty()) EmptyPanel(Icons.Default.EventAvailable, "Espacio para tus planes", "Añade una clase, tu trabajo o una actividad personal.")
    schedules.forEach { schedule ->
        val category = ActivityCategory.fromKey(schedule.category)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentIcon(category.icon, category.accentColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${schedule.startTime.take(5)} — ${schedule.endTime.take(5)}", color = category.accentColor, style = MaterialTheme.typography.labelLarge)
                    Text(schedule.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(category.label, category.accentColor)
                StatusPill(schedule.modality.replaceFirstChar(Char::titlecase), TextSecondary)
            }
            if (schedule.location.isNotBlank()) Text(schedule.location, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = DarkCardBorder)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editing = schedule; showEditor = true }, enabled = !state.busy) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Editar", color = IndigoPrimary)
                }
                IconButton(onClick = { pendingDelete = schedule }, enabled = !state.busy) { Icon(Icons.Default.DeleteOutline, "Eliminar ${schedule.title}", tint = TextMuted) }
            }
        }
    }
    if (showEditor) ScheduleEditor(editing, day, state, actions) { showEditor = false }
    pendingDelete?.let { schedule -> Confirm("¿Eliminar ${schedule.title}?", "Esta actividad se eliminará de tu horario.",
        { pendingDelete = null }, { actions.deleteSchedule(schedule.id); pendingDelete = null }) }
}

@Composable
private fun ScheduleEditor(existing: ScheduleDto?, initialDay: Int, state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(existing?.location.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(existing?.startTime?.take(5) ?: "09:00") }
    var end by rememberSaveable { mutableStateOf(existing?.endTime?.take(5) ?: "10:00") }
    var day by rememberSaveable { mutableStateOf(existing?.dayOfWeek ?: initialDay) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "university") }
    var modality by rememberSaveable { mutableStateOf(existing?.modality ?: "presencial") }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.notice) { if (submitted && !state.busy && state.notice == "Actividad guardada.") dismiss() }
    Sheet(dismiss, if (existing == null) "Una nueva actividad" else "Editar actividad", "Organiza tu semana a tu manera.") {
        Text("Tipo de actividad", style = MaterialTheme.typography.titleSmall)
        Choices(listOf("university", "work", "free"), category, { category = it }) { ActivityCategory.fromKey(it).label }
        Field(title, { title = it.take(120) }, "Título", placeholder = "¿Qué tienes planeado?")
        Choices((1..7).toList(), day, { day = it }) { days[it - 1].take(3) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { TimeField(start, { start = it }, "Inicio") }
            Box(Modifier.weight(1f)) { TimeField(end, { end = it }, "Fin") }
        }
        Choices(listOf("presencial", "virtual"), modality, { modality = it }) { it.replaceFirstChar(Char::titlecase) }
        Field(location, { location = it.take(160) }, "Lugar o aula (opcional)")
        if (submitted) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submitted = true; actions.saveSchedule(existing?.id, day, start, end, title, location, category, modality) },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar actividad") }
    }
}

@Composable
private fun TimeField(value: String, change: (String) -> Unit, label: String) {
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DarkCardBorder)) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(value, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        }
    }
    if (picking) {
        val time = rememberTimePickerState(initialHour = value.take(2).toIntOrNull() ?: 9, initialMinute = value.takeLast(2).toIntOrNull() ?: 0, is24Hour = true)
        AlertDialog(onDismissRequest = { picking = false }, title = { Text("Hora de ${label.lowercase()}") }, text = { TimeInput(time) },
            confirmButton = { TextButton(onClick = { change(String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)); picking = false }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun People(state: AccountState, actions: AccountActions) {
    var connect by rememberSaveable { mutableStateOf(false) }
    var manageMessages by rememberSaveable { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf<PersonConnection?>(null) }
    PageHeading("A tu lado", "Tu gente", "Un vistazo a su día. Un mensaje para estar cerca.")
    FilledTonalButton(onClick = { connect = true }, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = IndigoCardBg, contentColor = IndigoPrimary)) {
        Icon(Icons.Default.PersonAddAlt, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Conectar con alguien")
    }
    if (state.connections.isEmpty()) EmptyPanel(Icons.Default.PeopleOutline, "Lo cotidiano se comparte", "Conecta con una persona para ver sus estados y horarios. Tú decides con quién.")
    else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.connections.forEach { connection ->
                state.profiles.find { it.id == connection.otherPerson(state.profile?.id.orEmpty()) }?.let { person ->
                    FilterChip(selected = state.selectedPerson?.id == person.id, onClick = { actions.selectPerson(person) }, enabled = !state.busy,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IndigoCardBg, selectedLabelColor = IndigoPrimary),
                        leadingIcon = { Icon(Icons.Default.PersonOutline, null, Modifier.size(18.dp)) }, label = { Text(person.displayName) })
                }
            }
        }
    }
    state.selectedPerson?.let { person ->
        HeroStatusCard(sharedStatus(person, state.personSchedules, state.personStatuses))
        Panel {
            SectionHeading(Icons.Default.ChatBubbleOutline, "Un mensaje para ${person.displayName}", "Envía un mensaje sin cambiar tu estado.")
            if (state.messages.isEmpty()) Text("Guarda algo que suelas decir para enviarlo con un toque, también desde tu widget.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            state.messages.forEach { quick ->
                OutlinedButton(onClick = { actions.send(quick) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, IndigoCardBorder.copy(alpha = .5f))) {
                    Text(quick.label, Modifier.weight(1f), color = TextPrimary, textAlign = TextAlign.Start)
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar a ${person.displayName}", tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                }
            }
            TextButton(onClick = { manageMessages = true }) {
                Icon(Icons.Default.Tune, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Mis mensajes y widget", color = IndigoPrimary)
            }
            Text("Los mensajes aparecen en FindMe cuando la otra persona actualiza.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        SectionHeading(Icons.Default.CalendarMonth, "Su semana", "Actividades de ${person.displayName}")
        var personDay by rememberSaveable(person.id) { mutableStateOf(ZonedDateTime.now(person.zone()).dayOfWeek.value) }
        Choices((1..7).toList(), personDay, { personDay = it }) { days[it - 1].take(3) }
        val schedules = state.personSchedules.filter { it.dayOfWeek == personDay }.sortedBy { it.startTime }
        if (schedules.isEmpty()) Text("Sin actividades para el ${days[personDay - 1].lowercase()}.", color = TextMuted)
        schedules.forEachIndexed { index, schedule -> ActivityTimelineCard(schedule, false, last = index == schedules.lastIndex) }
        state.connections.find { it.otherPerson(state.profile?.id.orEmpty()) == person.id }?.let { connection ->
            TextButton(onClick = { disconnect = connection }, enabled = !state.busy) { Text("Administrar conexión con ${person.displayName}", color = TextMuted) }
        }
    }
    if (state.signals.isNotEmpty()) Panel {
        SectionHeading(Icons.Default.MarkChatUnread, "Te han escrito")
        state.signals.take(5).forEach { signal ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.profiles.find { it.id == signal.senderId }?.displayName ?: "Persona conectada", color = IndigoPrimary, style = MaterialTheme.typography.labelMedium)
                Text(signal.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (connect) ConnectionSheet(state, actions) { connect = false }
    if (manageMessages) MessageSheet(state, actions) { manageMessages = false }
    disconnect?.let { connection -> Confirm("¿Desvincular persona?", "Ambos dejarán de ver el horario y estado del otro.",
        { disconnect = null }, { actions.disconnect(connection.id); disconnect = null }) }
}

@Composable
private fun ConnectionSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("Compartir código") }
    var code by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(state.notice, state.busy) { if (submitted && !state.busy && state.notice == "Conexión establecida.") dismiss() }
    Sheet(dismiss, "Conecta con tu gente", "Al conectar, ambos podrán ver sus estados y horarios.") {
        Choices(listOf("Compartir código", "Tengo un código"), mode, { mode = it }) { it }
        if (mode == "Compartir código") {
            state.invitation?.let { invitation ->
                val display = InvitationCode.display(invitation.code)
                Surface(color = IndigoCardBg, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, IndigoCardBorder.copy(alpha = .5f))) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Key, null, tint = IndigoPrimary)
                        Text(display, color = TextPrimary, fontSize = 27.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                        Text("Válido 24 horas · Un solo uso", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Código FindMe", display))) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copiar")
                    }
                    Button(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Conecta conmigo en FindMe. Mi código es $display. Es válido durante 24 horas.") }, "Compartir invitación"))
                    }, modifier = Modifier.weight(1f)) { Text("Compartir") }
                }
            } ?: EmptyPanel(Icons.Default.Key, "Invita a una persona", "Envíale tu código y pídele que lo introduzca en su app.")
            Button(onClick = actions::invite, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.invitation == null) "Crear mi código" else "Generar otro código")
            }
        } else {
            Field(code, { code = it.take(40) }, "Código de invitación", placeholder = "ABCD-EFGH", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            Text("Introduce el código que esa persona compartió contigo.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { submitted = true; actions.accept(code) }, enabled = !state.busy && InvitationCode.isValid(code), modifier = Modifier.fillMaxWidth()) { Text("Conectar y compartir") }
        }
        if (submitted || state.invitation == null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MessageSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    val person = state.selectedPerson
    LaunchedEffect(state.notice) { if (state.notice == "Mensaje rápido creado.") message = "" }
    Sheet(dismiss, "Tus mensajes rápidos", "Guarda un mensaje que quieras enviar con un toque.") {
        Text("Por ejemplo, una pregunta o un saludo. Se envía a la persona que tengas seleccionada; no cambia tu estado.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Field(message, { message = it.take(240) }, "¿Qué quieres decir?", placeholder = "Escribe tu mensaje")
        Button(onClick = { actions.saveMessage(message.trim().take(30), message) }, enabled = !state.busy && message.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Guardar mensaje") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.messages.forEach { quick ->
            Panel {
                Text(quick.message, fontWeight = FontWeight.SemiBold)
                if (person != null) OutlinedButton(onClick = { actions.configureWidget(quick) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Widgets, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Usar en widget con ${person.displayName}")
                }
                TextButton(onClick = { actions.deleteMessage(quick.id) }, enabled = !state.busy) { Text("Eliminar mensaje", color = TextMuted) }
            }
        }
    }
}

@Composable
private fun AccountProfilePanel(state: AccountState, actions: AccountActions) {
    PageHeading("A tu manera", "Mi cuenta", "Tu información y las personas con quienes compartes.")
    Panel {
        SectionHeading(Icons.Default.PersonOutline, state.profile?.displayName.orEmpty(), state.profile?.timezone)
        ProfileNameForm(state, actions)
        HorizontalDivider(color = DarkCardBorder)
        Text("Tu horario y tus estados solo se comparten con las personas que conectes. Puedes retirar la conexión desde Personas.", color = TextSecondary)
        OutlinedButton(onClick = actions::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}

@Composable
private fun EmptyPanel(icon: ImageVector, title: String, description: String) {
    Panel {
        AccentIcon(icon, cyan)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Sheet(dismiss: () -> Unit, title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = DarkSurface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, placeholder: String? = null, keyboard: KeyboardOptions = KeyboardOptions.Default) {
    OutlinedTextField(value, change, label = { Text(label) }, placeholder = { placeholder?.let { Text(it, color = TextMuted) } },
        keyboardOptions = keyboard, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary, unfocusedBorderColor = DarkCardBorder, focusedLabelColor = IndigoPrimary))
}

@Composable
private fun <T> Choices(values: List<T>, selected: T, change: (T) -> Unit, label: (T) -> String) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = selected == value, onClick = { change(value) }, label = { Text(label(value)) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IndigoCardBg, selectedLabelColor = IndigoPrimary), shape = RoundedCornerShape(12.dp)) }
    }
}

@Composable
private fun Confirm(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, containerColor = DarkCardBg,
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
