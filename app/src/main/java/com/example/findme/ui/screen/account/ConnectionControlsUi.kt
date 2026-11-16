package com.example.findme.ui.screen.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ConnectionPreference
import com.example.findme.data.model.ConnectionSignal
import com.example.findme.ui.theme.DarkSurface
import com.example.findme.ui.theme.TextMuted
import com.example.findme.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One received message with the recipient-only actions: mark as read and delete. */
@Composable
internal fun ReceivedSignalItem(signal: ConnectionSignal, sender: String, busy: Boolean, actions: AccountActions) {
    val unread = signal.readAt == null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (unread) "$sender · Nuevo" else sender, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium, fontWeight = if (unread) FontWeight.Bold else null)
        Text(signal.message, style = MaterialTheme.typography.bodyMedium, fontWeight = if (unread) FontWeight.SemiBold else null)
        signal.createdAt?.let { timestamp ->
            runCatching {
                Instant.parse(timestamp).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm · d MMM", Locale.forLanguageTag("es")))
            }.getOrNull()?.let { Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (unread) TextButton(onClick = { actions.markSignalRead(signal.id) }, enabled = !busy) {
                Icon(Icons.Default.DoneAll, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Marcar como leído")
            }
            TextButton(onClick = { actions.deleteSignal(signal.id) }, enabled = !busy) {
                Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Borrar", color = TextMuted)
            }
        }
    }
}

/** Private nickname, what to share with this person and whether to silence them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionSettingsSheet(
    person: AccountProfile,
    current: ConnectionPreference,
    state: AccountState,
    actions: AccountActions,
    onDisconnect: () -> Unit,
    dismiss: () -> Unit
) {
    var nickname by rememberSaveable(person.id) { mutableStateOf(current.nickname.orEmpty()) }
    var shareSchedule by rememberSaveable(person.id) { mutableStateOf(current.shareSchedule) }
    var shareStatus by rememberSaveable(person.id) { mutableStateOf(current.shareStatus) }
    var muted by rememberSaveable(person.id) { mutableStateOf(current.muted) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.notice, state.busy) { if (submitted && !state.busy && state.notice == "Preferencias guardadas.") dismiss() }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = DarkSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tu conexión con ${person.displayName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Solo tú ves estas opciones. Se aplican únicamente a esta persona.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(nickname, { nickname = it.take(40) }, label = { Text("Apodo (opcional)") },
                placeholder = { Text(person.displayName, color = TextMuted) }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Text("El apodo solo lo ves tú, en lugar de su nombre.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            PreferenceSwitch("Compartir mi horario", "Si lo desactivas, esta persona deja de ver tus actividades.", shareSchedule) { shareSchedule = it }
            PreferenceSwitch("Compartir mi estado", "Si lo desactivas, esta persona deja de ver tu estado compartido.", shareStatus) { shareStatus = it }
            PreferenceSwitch("Silenciar avisos", "No recibirás notificaciones de sus mensajes ni de sus cambios de estado.", muted) { muted = it }
            Button(onClick = {
                submitted = true
                actions.saveConnectionPreference(current.copy(nickname = nickname.trim().ifEmpty { null },
                    shareSchedule = shareSchedule, shareStatus = shareStatus, muted = muted))
            }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            if (submitted) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDisconnect, enabled = !state.busy) {
                Text("Desvincular a ${person.displayName}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).toggleable(checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
