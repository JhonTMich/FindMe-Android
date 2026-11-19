@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.findme.ui.screen.account

import android.app.NotificationManager
import android.content.ClipData
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.findme.BuildConfig
import com.example.findme.data.local.AccountAlert
import com.example.findme.data.local.AppAccent
import com.example.findme.ui.screen.UpdateState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
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
import com.example.findme.domain.model.Registration
import com.example.findme.domain.model.Username
import com.example.findme.domain.model.InvitationCode
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.conflictMessage
import com.example.findme.domain.model.findScheduleConflict
import com.example.findme.domain.model.formatScheduleTime
import com.example.findme.domain.model.validateSchedule
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.data.model.ScheduleException
import com.example.findme.data.model.SharedSchedules
import com.example.findme.domain.model.commonFreeTime
import kotlinx.coroutines.CancellationException
import java.time.Instant
import com.example.findme.domain.model.timeRangeLabel
import com.example.findme.domain.model.nextActivityToday
import com.example.findme.ui.components.*
import com.example.findme.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.findme.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.shape.GenericShape

private val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val cyan = BrandSky
private fun AccountProfile.zone() = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())

@Composable
fun AccountScreen(viewModel: AccountViewModel, updates: UpdateState = UpdateState(),
    onCheckUpdates: () -> Unit = {}, onInstallUpdate: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            com.example.findme.push.NearMessagingService.observeAccountScreen { viewModel.observeWhileVisible() }
        }
    }
    AccountContent(state, viewModel, updates, onCheckUpdates, onInstallUpdate)
}

@Composable
fun AccountContent(state: AccountState, actions: AccountActions,
    updates: UpdateState = UpdateState(), onCheckUpdates: () -> Unit = {}, onInstallUpdate: () -> Unit = {}) {
    if (state.initializing) {
        Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandMark(Modifier.size(88.dp))
                CircularProgressIndicator(Modifier.size(24.dp), color = BrandAccent, strokeWidth = 2.dp)
            }
        }
        return
    }
    var tab by rememberSaveable(state.signedIn) { mutableStateOf("Mi día") }
    LaunchedEffect(state.peopleRevision, state.signedIn) { if (state.peopleRevision > 0 && state.signedIn) tab = "Personas" }
    LaunchedEffect(state.scheduleRevision, state.signedIn) { if (state.scheduleRevision > 0 && state.signedIn) tab = "Horario" }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it) } }
    // One continuous backdrop behind the top bar and the content, so no seam shows under the header.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppSurface, AppBackground), endY = 1400f))) {
    Scaffold(containerColor = Color.Transparent, snackbarHost = { NearSnackbarHost(snackbar) }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandMark(Modifier.size(46.dp))
            Column(Modifier.weight(1f)) {
                Text("Near", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
                Text(state.profile?.takeUnless { it.needsName }?.let { "Hola, ${it.displayName}" } ?: "Siempre cerca de tu gente", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (state.signedIn) {
                IconButton(onClick = actions::refresh, enabled = !state.busy) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Actualizar", tint = TextSecondary)
                }
            }
        }
    }, bottomBar = {
        if (state.signedIn && state.profile?.needsProfile == false) {
            Surface(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp), color = AppSurface,
                border = BorderStroke(1.dp, CardBorder)) {
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.height(76.dp)) {
                    listOf(Triple("Mi día", "Hoy", Icons.Default.Today), Triple("Horario", "Horario", Icons.Default.CalendarMonth),
                        Triple("Personas", "Personas", Icons.Default.PeopleOutline), Triple("Cuenta", "Perfil", Icons.Default.PersonOutline)).forEach { (id, title, icon) ->
                        NavigationBarItem(selected = tab == id, onClick = { tab = id },
                            icon = { Icon(icon, null, Modifier.size(22.dp)) },
                            label = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))
                    }
                }
            }
        }
    }) { padding ->
        Crossfade(tab, modifier = Modifier.fillMaxSize().padding(padding), label = "accountNavigation") { activeTab ->
            Column(Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                state.error?.let { error -> Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } }
                if (state.signedIn && state.syncIssue) Panel {
                    Text(
                        if (state.profile == null) "No pudimos cargar tu perfil."
                        else "Sincronización incompleta. Puedes seguir consultando los datos disponibles.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = actions::refresh, enabled = !state.busy) { Text("Reintentar sincronización") }
                }
                if (!state.signedIn) LoginForm(state, actions)
                else if (state.profile == null) {
                    Panel {
                        if (!state.syncIssue) Text("Cargando tu perfil…", color = TextSecondary)
                        OutlinedButton(onClick = actions::logout, enabled = !state.busy) { Text("Cerrar sesión") }
                    }
                }
                else if (state.profile.needsProfile) CompleteProfile(state, actions)
                else when (activeTab) {
                    "Mi día" -> MyDay(state, actions, onOpenSchedule = { tab = "Horario" })
                    "Horario" -> ScheduleManager(state, actions)
                    "Personas" -> People(state, actions)
                    else -> AccountProfilePanel(state, actions, updates, onCheckUpdates, onInstallUpdate)
                }
            }
        }
    }
    }
    if (state.passwordRecovery && state.signedIn) NewPasswordSheet(state, actions)
}

/** Shown after a recovery link signs the person in, to choose the new password. */
@Composable
private fun NewPasswordSheet(state: AccountState, actions: AccountActions) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Sheet(actions::cancelPasswordRecovery, "Elige una contraseña nueva", "De 8 a 128 caracteres. La usarás junto con tu nombre de usuario para entrar.") {
        listOf("Nueva contraseña" to password, "Confirmar contraseña" to confirmation).forEachIndexed { index, (label, value) ->
            OutlinedTextField(value, { if (index == 0) password = it.take(128) else confirmation = it.take(128) },
                label = { Text(label) }, singleLine = true, enabled = !state.busy,
                isError = index == 1 && confirmation.isNotEmpty() && confirmation != password,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Ocultar contraseña" else "Mostrar contraseña")
                } }, shape = FieldShape, modifier = Modifier.fillMaxWidth(), colors = nearTextFieldColors())
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { actions.setRecoveredPassword(password, confirmation) },
            enabled = !state.busy && password.length in 8..128 && password == confirmation, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Guardando…" else "Guardar contraseña")
        }
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (eyebrow.isNotBlank()) Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, CardBorder),
        colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CardBg, AppSurface)))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun SectionHeading(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentIcon(icon, MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LoginForm(state: AccountState, actions: AccountActions) {
    var mode by rememberSaveable(state.loginRevision) { mutableStateOf("login") }
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.confirmationEmail) {
        if (state.confirmationEmail != null) { password = ""; confirmation = "" }
    }
    state.confirmationEmail?.let { pendingEmail ->
        PageHeading("Un paso más", "Revisa tu correo", "Confirma tu dirección antes de iniciar sesión.")
        Panel {
            SectionHeading(Icons.Default.MarkEmailRead, "Tu cuenta, a un paso", pendingEmail)
            Text("Abre el enlace de confirmación y después vuelve a Near. Revisa también la carpeta de spam.", color = TextSecondary)
            Button(onClick = { mode = "login"; actions.finishRegistration() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Ya confirmé, iniciar sesión")
            }
            OutlinedButton(onClick = { actions.resendConfirmation(pendingEmail) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Reenviar confirmación")
            }
        }
        return
    }
    val registering = mode == "register"
    val recovering = mode == "recover"
    // Recovery reuses the email-only layout of "resend confirmation".
    val resending = mode == "resend" || recovering
    val title = when { registering -> "Tu gente, más cerca."; recovering -> "Recupera tu acceso."; resending -> "Confirma tu cuenta."; else -> "Qué bueno verte." }
    val subtitle = when { registering -> "Crea tu cuenta y confirma tu correo para empezar."
        recovering -> "Te enviaremos un enlace al correo de tu cuenta para elegir una contraseña nueva."
        resending -> "Te enviaremos otro enlace al correo con el que empezaste tu registro."
        else -> "Tu horario y tu gente, en un mismo lugar." }
    val passwordToggle: @Composable () -> Unit = {
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña")
        }
    }
    LoginStage {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        if (registering) Field(name, { name = it.take(80) }, "Nombre visible", leading = Icons.Default.Badge,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        if (registering || resending) Field(email, { email = it.take(254) }, "Correo electrónico", leading = Icons.Default.MailOutline,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
        if (!resending) Field(username, { username = it.take(24) }, "Nombre de usuario", leading = Icons.Default.PersonOutline,
            keyboard = KeyboardOptions(autoCorrectEnabled = false))
        if (registering) Text("De 3 a 24 letras sin tildes, números o guion bajo. Será tu usuario para entrar.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        if (!resending) Field(password, { password = it.take(128) }, "Contraseña", leading = Icons.Default.Lock,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            hidden = !passwordVisible, trailing = passwordToggle, enabled = !state.busy)
        if (registering) {
            Field(confirmation, { confirmation = it.take(128) }, "Confirmar contraseña", leading = Icons.Default.Lock,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                hidden = !passwordVisible, trailing = passwordToggle, enabled = !state.busy,
                isError = confirmation.isNotEmpty() && password != confirmation)
            Text(if (confirmation.isNotEmpty() && password != confirmation) "Las contraseñas no coinciden." else "Usa entre 8 y 128 caracteres. Confirma tu correo para empezar.",
                color = if (confirmation.isNotEmpty() && password != confirmation) MaterialTheme.colorScheme.error else TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        val valid = when {
            registering -> Registration.error(name, email, username, password, confirmation) == null
            resending -> Registration.isEmail(email)
            else -> Username.isValid(username) && password.isNotBlank()
        }
        Button(onClick = {
            when { registering -> actions.register(name, email, username, password, confirmation)
                recovering -> actions.requestPasswordReset(email)
                resending -> actions.resendConfirmation(email)
                else -> actions.login(username, password) }
        }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp), enabled = !state.busy && valid, shape = RoundedCornerShape(18.dp)) {
            Text(when { state.busy -> "Un momento…"; registering -> "Registrarme"; recovering -> "Enviar enlace"; resending -> "Enviar confirmación"; else -> "Entrar" },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (mode == "login") TextButton(onClick = { mode = "recover"; password = ""; passwordVisible = false }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("¿Olvidaste tu contraseña?", color = TextSecondary)
        }
        if (recovering) TextButton(onClick = { mode = "login" }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Volver a iniciar sesión", color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = { mode = when (mode) { "login", "resend" -> "register"; else -> "login" }; password = ""; confirmation = ""; passwordVisible = false }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(when (mode) { "login" -> "Crear cuenta"; "resend" -> "Volver al registro"; else -> "Ya tengo cuenta" },
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        if (registering) TextButton(onClick = { mode = "resend"; password = ""; confirmation = ""; passwordVisible = false }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("No recibí mi confirmación", color = TextMuted)
        }
    }
}

/** Darker panel under the sign-in screens; its top edge arcs like the ledge the mascot peeks over in the icon. */
private val LoginPanelColor = Color(0xFF181A33)
private val LedgeShape = GenericShape { size, _ ->
    val depth = size.width * .08f
    moveTo(0f, depth)
    quadraticTo(size.width / 2, -depth, size.width, depth)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** The mascot peeking over the arched panel: head behind it, paws resting on it, the form inside. */
@Composable
private fun LoginStage(content: @Composable ColumnScope.() -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // Bleed past the screen's 20dp padding so the panel reaches both edges and the bottom.
    Box(Modifier.fillMaxWidth().layout { measurable, constraints ->
        val side = 20.dp.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * side, maxWidth = constraints.maxWidth + 2 * side))
        layout(constraints.maxWidth, placeable.height - side) { placeable.place(-side, 0) }
    }) {
        Image(painterResource(R.drawable.near_mascot_head), null,
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(176.dp, 128.dp))
        Column(Modifier.padding(top = 116.dp).fillMaxWidth().heightIn(min = screenHeight - 190.dp)
            .background(LoginPanelColor, LedgeShape).padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Image(painterResource(R.drawable.near_mascot_paws), null,
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(176.dp, 128.dp))
    }
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
private fun profileClock(zone: ZoneId): ZonedDateTime {
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) { while (true) { now = ZonedDateTime.now(zone); delay(15_000) } }
    return now
}

@Composable
private fun contactStatus(person: AccountProfile, state: AccountState): UserCurrentStatus {
    val visibility = state.incomingVisibility[person.id]
        ?: return UserCurrentStatus.Error("Datos desactualizados. Actualiza para ver qué comparte.", person.displayName)
    if (!visibility.sharesSchedule && !visibility.sharesStatus) {
        return UserCurrentStatus.Error("Su horario y su estado son privados.", person.displayName)
    }
    if ((visibility.sharesSchedule && !state.peopleSchedulesFresh) ||
        (visibility.sharesStatus && !state.peopleStatusesFresh)) {
        return UserCurrentStatus.Error("Datos desactualizados. Actualiza para ver su actividad.", person.displayName)
    }
    val now = profileClock(person.zone())
    val status = StatusRepository.calculateStatus(person.displayName,
        state.personSchedules.takeIf { visibility.sharesSchedule }.orEmpty(),
        state.personStatuses.takeIf { visibility.sharesStatus }.orEmpty(), now.dayOfWeek.value, now)
    return if (!visibility.sharesSchedule && status is UserCurrentStatus.FreeTime) {
        UserCurrentStatus.Error("Horario privado. No hay un estado compartido en este momento.", person.displayName)
    } else status
}

@Composable
private fun MyDay(state: AccountState, actions: AccountActions, onOpenSchedule: () -> Unit) {
    var editStatus by rememberSaveable { mutableStateOf(false) }
    val profile = state.profile
    val now = profileClock(profile?.zone() ?: ZoneId.systemDefault())
    val status = profile?.let { StatusRepository.calculateStatus(it.displayName, state.schedules, state.statuses, now.dayOfWeek.value, now) }
    val next = nextActivityToday(state.schedules, now)
    PageHeading("", "Mi día", now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))))
    status?.let { current ->
        HeroStatusCard(current, heading = "Ahora") {
            Button(onClick = { editStatus = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                Icon(Icons.Default.Edit, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (current is UserCurrentStatus.Override) "Cambiar mi estado" else "Compartir mi estado")
            }
            if (current is UserCurrentStatus.Override) TextButton(onClick = actions::clearStatus, enabled = !state.busy) {
                Text("Volver al horario")
            }
        }
    }
    val today = state.schedules.occurrencesOn(now.toLocalDate(), now.zone)
    if (today.isEmpty()) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.near_mascot_head), null, modifier = Modifier.size(74.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Un día a tu ritmo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Nada más por hoy", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            TextButton(onClick = onOpenSchedule) { Text("Ver horario") }
        }
    } else {
        SectionHeading(Icons.Default.Schedule, "Después")
        NextActivityCard(next, onOpenSchedule)
        SectionHeading(Icons.Default.CalendarToday, "Agenda de hoy")
        today.forEachIndexed { index, occurrence ->
            ActivityTimelineCard(occurrence.schedule,
                active = status is UserCurrentStatus.InClass && !now.isBefore(occurrence.window.start) &&
                    now.isBefore(occurrence.window.end) && status.currentSchedule.id == occurrence.schedule.id,
                last = index == today.lastIndex, displayDay = now.dayOfWeek.value, compact = true)
        }
    }
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
    val zone = state.profile?.zone() ?: ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var day by rememberSaveable { mutableStateOf(today.dayOfWeek.value) }
    var oneOff by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    val dates = (0L..6L).map(monday::plusDays)
    val week = remember(state.schedules, monday, zone) {
        dates.map { state.schedules.occurrencesOn(it, zone) }
    }
    val date = dates[day - 1]
    val distinct = week.flatten().distinctBy { it.schedule.id to it.originalDate }.map { it.schedule }
    WeeklyScheduleHeader(distinct, day, onDaySelected = { day = it }, showDayTitle = false,
        today = today, occurrenceCounts = week.map { it.size })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(days[day - 1], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = { editing = null; showEditor = true }, enabled = !state.busy,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Añadir")
        }
    }
    val occurrences = week[day - 1]
    val exceptions = state.schedules.mapNotNull { schedule ->
        schedule.occurrenceExceptions[date.toString()]?.let { schedule to it }
    }.filter { (_, exception) -> exception.cancelled || exception.date != date.toString() }
    if (occurrences.isEmpty() && exceptions.isEmpty()) {
        EmptyPanel(Icons.Default.EventAvailable, "Espacio para tus planes", "Añade una clase, tu trabajo o una actividad personal.")
    }
    occurrences.forEach { occurrence ->
        val schedule = occurrence.schedule
        val recurring = state.schedules.first { it.id == schedule.id }
        val category = ActivityCategory.fromKey(schedule.category)
        val reprogrammed = recurring.occurrenceExceptions.containsKey(occurrence.originalDate.toString())
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentIcon(category.icon, category.accentColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(schedule.timeRangeLabel(day), color = category.accentColor, style = MaterialTheme.typography.labelLarge)
                    Text(schedule.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (reprogrammed) StatusPill("Reprogramada", BrandSky)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(category.label, category.accentColor)
                StatusPill(schedule.modality.replaceFirstChar(Char::titlecase), TextSecondary)
            }
            if (schedule.location.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = TextMuted)
                Text(schedule.location, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = CardBorder)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CardAction(Icons.Default.EventRepeat, "Solo esta fecha", enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    oneOff = recurring.id to occurrence.originalDate
                }
                CardAction(Icons.Default.Edit, "Serie semanal", enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    editing = recurring; showEditor = true
                }
                IconButton(onClick = { pendingDelete = recurring }, enabled = !state.busy,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = .10f))) {
                    Icon(Icons.Default.DeleteOutline, "Eliminar serie de ${schedule.title}", tint = MaterialTheme.colorScheme.error.copy(alpha = .85f))
                }
            }
        }
    }
    exceptions.forEach { (schedule, exception) ->
        Panel {
            Text(schedule.title, fontWeight = FontWeight.Bold)
            StatusPill(if (exception.cancelled) "Cancelada" else "Reprogramada",
                if (exception.cancelled) MaterialTheme.colorScheme.error else BrandSky)
            exception.date?.takeUnless { exception.cancelled }?.let {
                Text("Pasa al ${LocalDate.parse(it).longDate()}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            CardAction(Icons.Default.EventRepeat, "Solo esta fecha", enabled = !state.busy) { oneOff = schedule.id to date }
        }
    }
    oneOff?.let { (id, original) ->
        state.schedules.find { it.id == id }?.let { schedule ->
            ScheduleOccurrenceEditor(schedule, original, today, state, actions) { oneOff = null }
        }
    }
    if (showEditor) ScheduleEditor(editing, day, state, actions) { showEditor = false }
    pendingDelete?.let { schedule -> Confirm("¿Eliminar la serie de ${schedule.title}?", "Se eliminarán todas las repeticiones y sus cambios puntuales.",
        { pendingDelete = null }, { actions.deleteSchedule(schedule.id); pendingDelete = null }) }
}

private fun LocalDate.longDate(): String =
    format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es")))

@Composable
private fun ScheduleOccurrenceEditor(
    schedule: ScheduleDto, preferredDate: LocalDate, today: LocalDate,
    state: AccountState, actions: AccountActions, dismiss: () -> Unit
) {
    val next = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(schedule.dayOfWeek)))
    val originals = (0L..3L).map(next::plusWeeks).let { dates ->
        if (preferredDate >= today || schedule.occurrenceExceptions[preferredDate.toString()]?.date != null)
            (dates + preferredDate).distinct().sorted() else dates
    }
    var originalText by rememberSaveable { mutableStateOf(originals.firstOrNull { it == preferredDate }?.toString() ?: next.toString()) }
    val original = LocalDate.parse(originalText)
    val existing = schedule.occurrenceExceptions[originalText]
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    Sheet(dismiss, "Solo esta fecha", gesturesEnabled = false) {
        Text(schedule.title, fontWeight = FontWeight.Bold)
        ScheduleChoice("Fecha original", originals, original, { originalText = it.toString() }, leading = Icons.Default.Event) { it.longDate() }
        key(originalText) {
            var moving by rememberSaveable { mutableStateOf(existing?.date != null) }
            val moveDates = (0L..13L).map { maxOf(today, original.minusDays(6)).plusDays(it) }.let { dates ->
                (dates + listOfNotNull(existing?.date?.let(LocalDate::parse))).distinct().sorted()
            }
            var dateText by rememberSaveable { mutableStateOf(existing?.date ?: maxOf(original, today).toString()) }
            var start by rememberSaveable { mutableStateOf(existing?.startTime ?: schedule.startTime) }
            var end by rememberSaveable { mutableStateOf(existing?.endTime ?: schedule.endTime) }
            var overnight by rememberSaveable { mutableStateOf(existing?.endsNextDay ?: schedule.endsNextDay) }
            Choices(listOf(false, true), moving, { moving = it }) { if (it) "Reprogramar" else "Cancelar actividad" }
            if (moving) {
                ScheduleChoice("Nueva fecha", moveDates, LocalDate.parse(dateText), { dateText = it.toString() }, leading = Icons.Default.EventRepeat) { it.longDate() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { TimeField(start, { start = it }, "Inicio") }
                    Box(Modifier.weight(1f)) { TimeField(end, { end = it }, "Fin") }
                }
                Row(Modifier.fillMaxWidth().toggleable(overnight, role = Role.Switch, onValueChange = { overnight = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Termina al día siguiente", modifier = Modifier.weight(1f))
                    Switch(checked = overnight, onCheckedChange = null, colors = nearSwitchColors())
                }
            }
            val validation = if (moving) validateSchedule(schedule.dayOfWeek, start, end, schedule.title, overnight) else null
            (validation ?: state.error?.takeIf { submitted })?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                submitted = true
                val change = if (moving) ScheduleException(date = dateText, startTime = start, endTime = end, endsNextDay = overnight)
                    else ScheduleException(cancelled = true)
                actions.changeScheduleOccurrence(draftId, schedule.id, originalText, change)
            }, enabled = !state.busy && validation == null, modifier = Modifier.fillMaxWidth()) {
                Text(if (moving) "Guardar reprogramación" else "Cancelar solo esta fecha")
            }
            if (existing != null) TextButton(onClick = {
                submitted = true
                actions.changeScheduleOccurrence(draftId, schedule.id, originalText, null)
            }, enabled = !state.busy) { Text("Restaurar horario habitual") }
        }
    }
}

@Composable
private fun ScheduleEditor(existing: ScheduleDto?, initialDay: Int, state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    val editingId by rememberSaveable { mutableStateOf(existing?.id) }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(existing?.location.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(existing?.startTime ?: "09:00") }
    var end by rememberSaveable { mutableStateOf(existing?.endTime ?: "10:00") }
    var endsNextDay by rememberSaveable { mutableStateOf(existing?.endsNextDay ?: false) }
    var selectedDays by rememberSaveable { mutableStateOf(listOf(existing?.dayOfWeek ?: initialDay)) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "university") }
    var modality by rememberSaveable { mutableStateOf(existing?.modality ?: "presencial") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val activities = selectedDays.map { day ->
        val id = editingId ?: UUID.nameUUIDFromBytes("$draftId:$day".toByteArray(Charsets.UTF_8)).toString()
        ScheduleDto(id = id, dayOfWeek = day, startTime = start.trim(), endTime = end.trim(),
            title = title.trim(), location = location.trim(), category = category, modality = modality,
            endsNextDay = endsNextDay)
    }
    val invalid = selectedDays.firstOrNull()?.let { validateSchedule(it, start, end, title, endsNextDay) }
    val conflict = if (invalid == null) {
        activities.firstNotNullOfOrNull { findScheduleConflict(state.schedules, it) }
    } else null
    val timeError = validateSchedule(1, start, end, "Actividad", endsNextDay)
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    Sheet(dismiss, if (editingId == null) "Nueva actividad" else "Editar serie semanal", gesturesEnabled = false) {
        Field(title, { title = it.take(120) }, "Título", placeholder = "Ej. Clase de inglés")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editingId == null) {
                ScheduleDays(selectedDays, { selectedDays = it }, Modifier.weight(1f))
            } else {
                ScheduleChoice("Día", (1..7).toList(), selectedDays.single(),
                    { selectedDays = listOf(it) }, Modifier.weight(1f)) { days[it - 1] }
            }
            ScheduleChoice("Tipo", ActivityCategory.entries, ActivityCategory.fromKey(category),
                { category = it.key }, Modifier.weight(1f), leading = ActivityCategory.fromKey(category).icon) { it.label }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { TimeField(start, { start = it }, "Inicio") }
            Box(Modifier.weight(1f)) { TimeField(end, { end = it }, "Fin") }
        }
        Row(
            Modifier.fillMaxWidth().toggleable(value = endsNextDay, role = Role.Switch, enabled = !state.busy) {
                endsNextDay = it
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Termina al día siguiente", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = endsNextDay, onCheckedChange = null, colors = nearSwitchColors())
        }
        ScheduleChoice("Modalidad", listOf("presencial", "virtual"), modality, { modality = it },
            leading = if (modality == "virtual") Icons.Default.Videocam else Icons.Default.LocationOn) {
            it.replaceFirstChar(Char::titlecase)
        }
        val feedback = if (selectedDays.isEmpty()) "Elige al menos un día."
            else conflict?.conflictMessage() ?: timeError ?: if (submitted) invalid ?: state.error else null
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Field(location, { location = it.take(160) }, "Lugar o aula (opcional)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = dismiss) { Text("Cancelar") }
            Button(onClick = {
                submitted = true
                if (invalid == null) actions.saveSchedules(draftId, activities)
            }, enabled = !state.busy && selectedDays.isNotEmpty() && conflict == null,
                modifier = Modifier.weight(1f)) { Text("Guardar actividad") }
        }
    }
}

@Composable
private fun ScheduleDays(chosen: List<Int>, onSelect: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    val summary = when (chosen.size) {
        0 -> "Elegir días"
        1 -> days[chosen.single() - 1]
        in 2..3 -> chosen.joinToString(", ") { days[it - 1].take(3) }
        7 -> "Todos los días"
        else -> "${chosen.size} días"
    }
    SelectMenuField("Días", summary, modifier) { close ->
        days.forEachIndexed { index, name ->
            val day = index + 1
            val checked = day in chosen
            SelectMenuItem(name, checked, showCheck = false,
                leading = { Checkbox(checked = checked, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = TextMuted, checkmarkColor = MaterialTheme.colorScheme.onPrimary)) },
                onClick = { onSelect(if (checked) chosen - day else (chosen + day).sorted()) })
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = CardBorder)
        DropdownMenuItem(text = { Text("Listo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, onClick = close)
    }
}

@Composable
private fun <T> ScheduleChoice(
    label: String,
    choices: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    name: (T) -> String
) {
    SelectMenuField(label, name(selected), modifier, leading = leading) { close ->
        choices.forEach { choice ->
            SelectMenuItem(name(choice), choice == selected, onClick = { onSelect(choice); close() })
        }
    }
}

@Composable
private fun TimeField(value: String, change: (String) -> Unit, label: String) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SelectField(label, formatScheduleTime(value), onClick = { picking = true }, expanded = picking,
        trailing = Icons.Default.Schedule)
    if (picking) {
        val initial = StatusRepository.parseLocalTimeOrNull(value) ?: LocalTime.of(9, 0)
        ScheduleTimePicker("Hora de ${label.lowercase()}", initial, dismiss = { picking = false }) { time ->
            change(time.format(hourFormat))
            picking = false
        }
    }
}

@Composable
private fun CommonAvailabilitySheet(person: AccountProfile, actions: AccountActions, dismiss: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var schedules by remember { mutableStateOf<SharedSchedules?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var allHours by rememberSaveable { mutableStateOf(false) }
    var asOf by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(lifecycle, person.id, retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                loading = true
                schedules = null
                error = false
                try {
                    schedules = actions.sharedSchedulesForAvailability(person.id)
                    asOf = Instant.now()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = true
                } finally {
                    loading = false
                }
                delay(60_000)
            }
        }
    }

    Sheet(dismiss, "Horas libres con ${person.displayName}") {
        val pair = schedules
        when {
            loading -> Text("Consultando ambos horarios…", color = TextSecondary)
            error -> {
                Text("No pudimos consultar la disponibilidad.", color = TextSecondary)
                TextButton(onClick = { retry++ }) { Text("Reintentar") }
            }
            pair == null -> Text("Ambas personas deben compartir su horario en esta conexión.", color = TextSecondary)
            else -> {
                Text("Próximos 7 días. Huecos de 30 minutos o más, en tu zona horaria. Se calculan a partir de sus actividades programadas.",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Choices(listOf(false, true), allHours, { allHours = it }) { if (it) "Todo el día" else "8:00–22:00" }
                val freeDays = remember(pair, allHours, asOf) { commonFreeTime(pair, asOf, allHours) }
                freeDays.forEach { day ->
                    Surface(shape = RoundedCornerShape(18.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(day.date.longDate().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (day.slots.isNotEmpty()) Text("${day.slots.size} ${if (day.slots.size == 1) "hueco" else "huecos"}",
                                    color = TextMuted, style = MaterialTheme.typography.labelMedium)
                            }
                            if (day.slots.isEmpty()) Text("Sin huecos de 30 min", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                day.slots.forEach { slot ->
                                    val end = if (slot.end.toLocalDate() > day.date) "24:00" else slot.end.format(hourFormat)
                                    StatusPill("${slot.start.format(hourFormat)} – $end", BrandSky)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun People(state: AccountState, actions: AccountActions) {
    var connect by rememberSaveable { mutableStateOf(false) }
    var manageMessages by rememberSaveable { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf<PersonConnection?>(null) }
    var manageConnection by remember { mutableStateOf<PersonConnection?>(null) }
    val controls = state.controls
    PageHeading("", "Tu gente", "")
    state.lastSync?.let { updated ->
        Text("Actualizado ${updated.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
    FilledTonalButton(onClick = { connect = true }, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)) {
        Icon(Icons.Default.PersonAddAlt, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Conectar con alguien")
    }
    if (state.connections.isEmpty()) EmptyPanel(Icons.Default.PeopleOutline, "Lo cotidiano se comparte", "Conecta con una persona para ver sus estados y horarios. Tú decides con quién.")
    else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.connections.forEach { connection ->
                state.profiles.find { it.id == connection.otherPerson(state.profile?.id.orEmpty()) }?.let { person ->
                    ChoiceChip(controls.nameOf(person), state.selectedPerson?.id == person.id, onClick = { actions.selectPerson(person) },
                        enabled = !state.busy,
                        leading = { Icon(if (controls.isMuted(person.id)) Icons.Default.NotificationsOff else Icons.Default.PersonOutline, null, Modifier.size(18.dp)) })
                }
            }
        }
    }
    if (state.signals.isNotEmpty()) Panel {
        SectionHeading(Icons.Default.MarkChatUnread, "Te han escrito", "Mensajes recientes · Últimas 24 horas")
        state.signals.forEach { signal ->
            ReceivedSignalItem(signal, state.profiles.find { it.id == signal.senderId }?.let(controls::nameOf) ?: "Persona conectada",
                state.busy, actions)
        }
    }
    state.selectedPerson?.let { selected ->
        val person = selected.copy(displayName = controls.nameOf(selected))
        val visibility = state.incomingVisibility[person.id]
        key(person.id) {
            var showAvailability by rememberSaveable { mutableStateOf(false) }
            var sendMessage by rememberSaveable { mutableStateOf(false) }
            HeroStatusCard(contactStatus(person, state)) {
                Button(onClick = { sendMessage = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar mensaje")
                }
                OutlinedButton(onClick = { showAvailability = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.EventAvailable, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ver horas libres en común")
                }
                TextButton(onClick = { manageMessages = true }) { Text("Mis mensajes") }
            }
            if (showAvailability) CommonAvailabilitySheet(person, actions) { showAvailability = false }
            if (sendMessage) Sheet({ sendMessage = false }, "Mensaje para ${person.displayName}", "Los mensajes caducan después de 24 horas.") {
                if (state.messages.isEmpty()) Text("Crea un mensaje rápido para enviarlo desde aquí.", color = TextSecondary)
                state.messages.forEach { quick ->
                    OutlinedButton(onClick = { actions.send(quick); sendMessage = false }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(quick.label, Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    }
                }
                TextButton(onClick = { sendMessage = false; manageMessages = true }) { Text("Editar mensajes rápidos") }
            }
        }
        SectionHeading(Icons.Default.CalendarMonth, "Su semana")
        when {
            visibility?.sharesSchedule == false -> Panel {
                Text("Horario privado", fontWeight = FontWeight.Bold)
                Text("Esta persona no comparte su horario contigo.", color = TextSecondary)
            }
            visibility == null || !state.peopleSchedulesFresh -> Panel {
                Text("Datos desactualizados", fontWeight = FontWeight.Bold)
                TextButton(onClick = actions::refresh, enabled = !state.busy) { Text("Actualizar horario") }
            }
            else -> {
                var personDay by rememberSaveable(person.id) { mutableStateOf(ZonedDateTime.now(person.zone()).dayOfWeek.value) }
                SegmentedChoice((1..7).toList(), personDay, { personDay = it }) { days[it - 1].take(3) }
                val personDate = LocalDate.now(person.zone()).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(personDay)))
                Text(personDate.longDate(), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                val schedules = state.personSchedules.occurrencesOn(personDate, person.zone()).map { it.schedule }
                if (schedules.isEmpty()) Text("Sin actividades programadas", color = TextSecondary)
                schedules.forEachIndexed { index, schedule -> ActivityTimelineCard(schedule, false,
                    last = index == schedules.lastIndex, displayDay = personDay) }
            }
        }
        state.connections.find { it.otherPerson(state.profile?.id.orEmpty()) == person.id }?.let { connection ->
            TextButton(onClick = { manageConnection = connection }, enabled = !state.busy) { Text("Administrar conexión con ${person.displayName}", color = TextMuted) }
        }
    }
    if (connect) ConnectionSheet(state, actions) { connect = false }
    if (manageMessages) MessageSheet(state, actions) { manageMessages = false }
    manageConnection?.let { connection ->
        val person = state.profiles.find { it.id == connection.otherPerson(state.profile?.id.orEmpty()) }
        val preference = person?.let { controls.forPerson(it.id) }
        if (person != null && preference != null) ConnectionSettingsSheet(person, preference, state, actions,
            onDisconnect = { manageConnection = null; disconnect = connection }) { manageConnection = null }
    }
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
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Text(display, color = TextPrimary, fontSize = 27.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                        Text("Válido 24 horas · Un solo uso", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Código Near", display))) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copiar")
                    }
                    Button(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Conecta conmigo en Near. Mi código es $display. Es válido durante 24 horas.") }, "Compartir invitación"))
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
    LaunchedEffect(state.notice) { if (state.notice == "Mensaje rápido creado.") message = "" }
    Sheet(dismiss, "Tus mensajes rápidos", "Guarda un mensaje que quieras enviar con un toque.") {
        Text("Por ejemplo, una pregunta o un saludo. Se envía a la persona que tengas seleccionada; no cambia tu estado.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Field(message, { message = it.take(240) }, "¿Qué quieres decir?", placeholder = "Escribe tu mensaje")
        Button(onClick = { actions.saveMessage(message.trim().take(30), message) }, enabled = !state.busy && message.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Guardar mensaje") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.messages.forEach { quick ->
            Panel {
                Text(quick.message, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { actions.deleteMessage(quick.id) }, enabled = !state.busy) { Text("Eliminar mensaje", color = TextMuted) }
            }
        }
    }
}

@Composable
private fun AccountProfilePanel(state: AccountState, actions: AccountActions, updates: UpdateState,
    onCheckUpdates: () -> Unit, onInstallUpdate: () -> Unit) {
    val context = LocalContext.current
    val notificationManager = remember(context) { context.getSystemService(NotificationManager::class.java) }
    fun blockedChannels() = com.example.findme.util.NotificationHelper.Channel.entries.filter {
        notificationManager?.getNotificationChannel(it.id)?.importance == NotificationManager.IMPORTANCE_NONE
    }
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var silencedChannels by remember { mutableStateOf(blockedChannels()) }
    var exact by remember { mutableStateOf(com.example.findme.reminders.ActivityReminders.exactAllowed(context)) }
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        silencedChannels = blockedChannels()
        exact = com.example.findme.reminders.ActivityReminders.exactAllowed(context)
    }
    val nearAlerts = with(state.settings) { activities || statuses || messages || reminders }
    PageHeading("", "Perfil y ajustes", "")
    Panel {
        SectionHeading(Icons.Default.PersonOutline, state.profile?.displayName.orEmpty(), state.profile?.timezone)
        ProfileNameForm(state, actions)
    }
    Panel {
        SectionHeading(Icons.Default.NotificationsNone, "Notificaciones")
        Text(if (nearAlerts) "Near · Hay avisos activados" else "Near · Todos los avisos desactivados",
            color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(if (notificationsAllowed) "Android · Permite las notificaciones" else "Android · Bloquea las notificaciones",
            color = if (notificationsAllowed) TextSecondary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
            Text("Abrir ajustes de Android")
        }
        if (notificationsAllowed) silencedChannels.forEach { channel ->
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)) }) {
                Text("Android silenció: ${channel.title}")
            }
        }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Inicio de actividades", "Cuando empieza una actividad de tu gente.", state.settings.activities) { actions.setAlert(AccountAlert.Activities, it) }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Cambios de estado", "Cuando tu gente cambia de planes.", state.settings.statuses) { actions.setAlert(AccountAlert.Statuses, it) }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Mensajes recibidos", "", state.settings.messages) { actions.setAlert(AccountAlert.Messages, it) }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Antes de tus actividades", "", state.settings.reminders) { actions.setReminders(it) }
        if (state.settings.reminders) {
            Choices(com.example.findme.data.local.REMINDER_CHOICES, state.settings.reminderMinutes, actions::setReminderMinutes) { minutes ->
                if (minutes >= 60) "${minutes / 60} h antes" else "$minutes min antes"
            }
            if (!exact && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Text("Android puede retrasar el recordatorio unos minutos. Permite las alarmas exactas para que llegue a tiempo.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }) {
                    Text("Permitir alarmas exactas")
                }
            }
        }
    }
    Panel {
        SectionHeading(Icons.Default.Palette, "Apariencia")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppAccent.entries.forEach { accent ->
                ChoiceChip(accent.label, state.settings.accent == accent, onClick = { actions.setAccent(accent) },
                    modifier = Modifier.weight(1f),
                    leading = { Box(Modifier.size(12.dp).background(brandAccent(accent), CircleShape)) })
            }
        }
    }
    Panel {
        SectionHeading(Icons.Default.SystemUpdate, "Actualizaciones", "Near ${BuildConfig.VERSION_NAME}")
        Text(updates.message, color = TextSecondary)
        OutlinedButton(onClick = onCheckUpdates, enabled = !updates.checking && !updates.downloading) { Text(if (updates.checking) "Comprobando…" else "Buscar actualizaciones") }
        if (updates.release != null && !updates.downloading) TextButton(onClick = onInstallUpdate) { Text("Actualizar ahora") }
    }
    var accountSheet by remember { mutableStateOf<AccountSheet?>(null) }
    val scope = rememberCoroutineScope()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val data = actions.exportData()
        if (uri == null || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } != null }.getOrDefault(false)
            }
            actions.exportFinished(saved)
        }
    }
    Panel {
        SectionHeading(Icons.Default.PrivacyTip, "Tu privacidad")
        Text("Tu horario y tus estados solo se comparten con las personas que conectes. Puedes retirar la conexión desde Personas.", color = TextSecondary)
        OutlinedButton(onClick = { accountSheet = AccountSheet.Password }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Cambiar contraseña") }
        OutlinedButton(onClick = { exportLauncher.launch("near-${state.profile?.username ?: "datos"}.json") },
            enabled = !state.busy && state.profile != null, modifier = Modifier.fillMaxWidth()) { Text("Exportar mis datos") }
        OutlinedButton(onClick = actions::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
        TextButton(onClick = { accountSheet = AccountSheet.Delete }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Eliminar mi cuenta", color = MaterialTheme.colorScheme.error)
        }
    }
    when (accountSheet) {
        AccountSheet.Password -> ChangePasswordSheet(state, actions) { accountSheet = null }
        AccountSheet.Delete -> DeleteAccountSheet(state, actions) { accountSheet = null }
        null -> Unit
    }
}

private enum class AccountSheet { Password, Delete }

/** Closes a sheet once its own request finishes without an error. */
@Composable
private fun CloseWhenDone(state: AccountState, submitted: Boolean, onDone: () -> Unit, onFailed: () -> Unit) {
    LaunchedEffect(state.busy, submitted) {
        if (submitted && !state.busy) { if (state.error == null) onDone() else onFailed() }
    }
}

@Composable
private fun ChangePasswordSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    CloseWhenDone(state, submitted, dismiss) { submitted = false }
    Sheet(dismiss, "Cambiar contraseña", "Confirma tu contraseña actual y elige una nueva de 8 a 128 caracteres.") {
        PasswordField(current, { current = it.take(128) }, "Contraseña actual", !state.busy)
        PasswordField(new, { new = it.take(128) }, "Nueva contraseña", !state.busy)
        PasswordField(confirmation, { confirmation = it.take(128) }, "Confirmar nueva contraseña", !state.busy,
            isError = confirmation.isNotEmpty() && confirmation != new)
        if (submitted || state.error != null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submitted = true; actions.changePassword(current, new, confirmation) },
            enabled = !state.busy && current.isNotEmpty() && new.length in 8..128 && new == confirmation,
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Guardando…" else "Guardar contraseña") }
    }
}

@Composable
private fun DeleteAccountSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    CloseWhenDone(state, submitted, dismiss) { submitted = false }
    Sheet(dismiss, "¿Eliminar tu cuenta?", "Se borran tu perfil, tu horario, tus estados, tus mensajes rápidos y tus conexiones. " +
        "Tu gente dejará de verte. No se puede deshacer. Si quieres conservar una copia, usa antes «Exportar mis datos».") {
        PasswordField(password, { password = it.take(128) }, "Tu contraseña", !state.busy)
        if (submitted || state.error != null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submitted = true; actions.deleteAccount(password) }, enabled = !state.busy && password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Eliminando…" else "Eliminar definitivamente") }
        TextButton(onClick = dismiss, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: String, enabled: Boolean, isError: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = enabled, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Ocultar contraseña" else "Mostrar contraseña")
            }
        }, shape = FieldShape, modifier = Modifier.fillMaxWidth(), colors = nearTextFieldColors())
}

@Composable
private fun AccountSettingsSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null, colors = nearSwitchColors())
    }
}

/** Snackbars on a raised card with the accent for actions, instead of the cream inverse surface. */
@Composable
private fun NearSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(data, Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp),
            containerColor = InnerBg, contentColor = TextPrimary,
            actionColor = MaterialTheme.colorScheme.primary)
    }
}

/** Compact secondary action used inside cards, with an icon so it reads as a button and not as loose text. */
@Composable
private fun CardAction(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f),
            contentColor = MaterialTheme.colorScheme.primary)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
private fun Sheet(
    dismiss: () -> Unit,
    title: String,
    description: String? = null,
    gesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = AppSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = gesturesEnabled,
        dragHandle = if (gesturesEnabled) { { BottomSheetDefaults.DragHandle() } } else null
    ) {
        val scroll = rememberScrollState()
        val scrollModifier = if (gesturesEnabled) Modifier.verticalScroll(scroll).imePadding()
            else Modifier.imePadding().verticalScroll(scroll, overscrollEffect = null)
        Column(Modifier.fillMaxWidth().then(scrollModifier).padding(horizontal = 22.dp)
            .padding(top = if (gesturesEnabled) 0.dp else 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (gesturesEnabled) 16.dp else 12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            description?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
            content()
        }
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, placeholder: String? = null, keyboard: KeyboardOptions = KeyboardOptions.Default,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null, trailing: (@Composable () -> Unit)? = null,
    hidden: Boolean = false, enabled: Boolean = true, isError: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leading?.let { icon -> { Icon(icon, null, Modifier.size(20.dp)) } }, trailingIcon = trailing,
        visualTransformation = if (hidden) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = keyboard, singleLine = true, enabled = enabled, isError = isError,
        shape = FieldShape, modifier = Modifier.fillMaxWidth(), colors = nearTextFieldColors())
}

@Composable
private fun <T> Choices(values: List<T>, selected: T, change: (T) -> Unit, label: (T) -> String) {
    if (values.size <= 2) {
        SegmentedChoice(values, selected, change, label = label)
    } else {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value -> ChoiceChip(label(value), selected == value, onClick = { change(value) }) }
        }
    }
}

@Composable
private fun Confirm(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, containerColor = CardBg,
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
