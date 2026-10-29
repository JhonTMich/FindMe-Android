package com.example.findme.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.findme.ui.screen.UpdateState

@Composable
fun AppUpdateDialog(state: UpdateState, onUpdate: () -> Unit, onLater: () -> Unit, onCancelDownload: () -> Unit) {
    if (!state.showDialog || state.release == null) return
    AlertDialog(onDismissRequest = { if (!state.downloading) onLater() },
        title = { Text(if (state.downloading) "Actualizando FindMe" else "Actualización disponible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message)
                if (state.downloading) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            if (!state.downloading) TextButton(onClick = onUpdate) { Text("Actualizar") }
        },
        dismissButton = {
            TextButton(onClick = if (state.downloading) onCancelDownload else onLater) {
                Text(if (state.downloading) "Cancelar" else "Más tarde")
            }
        })
}
