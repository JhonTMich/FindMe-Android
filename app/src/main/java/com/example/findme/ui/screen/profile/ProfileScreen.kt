package com.example.findme.ui.screen.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.findme.ui.components.AccentIcon
import com.example.findme.ui.components.RevealCard
import com.example.findme.ui.components.StatusPill
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.findme.BuildConfig
import com.example.findme.data.local.UserSession
import com.example.findme.ui.screen.UpdateState
import com.example.findme.ui.theme.*

@Composable
fun ProfileScreen(userSession: UserSession?, activityAlerts: Boolean, statusAlerts: Boolean,
    onActivityAlerts: (Boolean) -> Unit, onStatusAlerts: (Boolean) -> Unit,
    updateState: UpdateState, onCheckUpdate: () -> Unit, onOpenRelease: () -> Unit,
    onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    var confirmUnlink by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10172A), DarkBackground), endY = 1100f)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Perfil y ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        RevealCard {
            Card(shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = .25f)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF28294F), Color(0xFF152538)))).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = CircleShape, color = IndigoPrimary.copy(alpha = .2f), border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = .4f))) {
                            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                                Text(userSession?.motherName?.take(1)?.uppercase() ?: "P", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(userSession?.motherName ?: "Mi perfil", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Tu espacio en FindMe", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    StatusPill("Cuenta de monitoreo", IndigoPrimary)
                    HorizontalDivider(color = Color.White.copy(alpha = .1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccentIcon(Icons.Default.FavoriteBorder, Color(0xFF67D8E8))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("PERSONA VINCULADA", style = MaterialTheme.typography.labelSmall, color = TextSecondary, letterSpacing = .8.sp)
                            Text(userSession?.displayName.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("Código: ${userSession?.familyCode.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
        SettingsCard {
            SettingsHeading(Icons.Default.NotificationsNone, "Notificaciones", "Elige qué quieres recibir", Color(0xFFFFC778))
            SettingsSwitch("Inicio de actividades", "Avisos cuando comienza una actividad del horario.", activityAlerts, onActivityAlerts)
            HorizontalDivider(color = DarkCardBorder)
            SettingsSwitch("Cambios de estado", "Avisos sobre los estados compartidos.", statusAlerts, onStatusAlerts)
            Text(if (notificationsAllowed)
                "También puedes ajustar los permisos y sonidos en Android."
                else "Android tiene bloqueadas las notificaciones de FindMe. Habilítalas en los ajustes del sistema.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Abrir ajustes de Android") }
        }
        SettingsCard {
            SettingsHeading(Icons.Default.SystemUpdate, "Actualizaciones", "Siempre al día", Color(0xFF67D8E8))
            StatusPill("FindMe ${BuildConfig.VERSION_NAME}", Color(0xFF67D8E8))
            Text(updateState.message, style = MaterialTheme.typography.bodyMedium)
            if (updateState.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCheckUpdate, enabled = !updateState.checking) {
                Text(if (updateState.checking) "Comprobando…" else "Buscar actualizaciones")
            }
            if (updateState.release != null) TextButton(onClick = onOpenRelease) { Text("Ver nueva versión") }
        }
        OutlinedButton(onClick = { confirmUnlink = true }, modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .3f)), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Desvincular cuenta", color = MaterialTheme.colorScheme.error)
        }
        Text("FindMe · Acompañar desde cerca", color = TextMuted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
    }
    if (confirmUnlink) AlertDialog(onDismissRequest = { confirmUnlink = false },
        title = { Text("¿Desvincular cuenta?") },
        text = { Text("Dejarás de ver las actividades de esta persona en este dispositivo. Puedes volver a vincularte con su código.") },
        confirmButton = { TextButton(onClick = { confirmUnlink = false; onLogout() }) { Text("Desvincular") } },
        dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancelar") } })
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, DarkCardBorder), colors = CardDefaults.cardColors(containerColor = DarkCardBg)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingsSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.material3.Surface(color = DarkCardBg) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(checked = checked, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF716CE6), uncheckedBorderColor = TextMuted))
        }
    }
}

@Composable
private fun SettingsHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentIcon(icon, color)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
