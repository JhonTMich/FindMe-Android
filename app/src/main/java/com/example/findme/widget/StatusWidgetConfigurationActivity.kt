package com.example.findme.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.findme.ui.theme.FindmeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class StatusWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent {
            FindmeTheme {
                var data by remember { mutableStateOf<WidgetData?>(null) }
                var loading by remember { mutableStateOf(true) }
                var saving by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                var revision by remember { mutableIntStateOf(0) }
                LaunchedEffect(revision) {
                    loading = true
                    error = null
                    try {
                        withTimeout(15_000) { WidgetStore.refresh(this@StatusWidgetConfigurationActivity) }
                        data = WidgetStore.data(this@StatusWidgetConfigurationActivity)
                        if (data == null) error = "Inicia sesión en FindMe y vuelve a añadir el widget."
                    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                        error = "La consulta tardó demasiado. Inténtalo de nuevo."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "No se pudo consultar tu gente. Comprueba tu conexión." }
                    finally { loading = false }
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Estado actual", style = MaterialTheme.typography.headlineMedium)
                        Text("Elige de quién quieres ver el estado y la próxima actividad. Cada widget puede mostrar una persona distinta.")
                        if (loading) CircularProgressIndicator()
                        error?.let {
                            Text(it)
                            Button(onClick = { revision++ }) { Text("Reintentar") }
                        }
                        if (!loading && error == null) data?.let { snapshot ->
                            snapshot.profiles.sortedBy { if (it.id == snapshot.owner) "" else it.displayName }.forEach { person ->
                                OutlinedButton(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
                                    saving = true
                                    lifecycleScope.launch {
                                        try {
                                            WidgetStore.select(this@StatusWidgetConfigurationActivity, id, snapshot.owner, person.id)
                                            GlanceWidgetWorker.enqueuePeriodicUpdate(this@StatusWidgetConfigurationActivity)
                                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                            finish()
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { saving = false; error = "No se pudo guardar. Inténtalo de nuevo." }
                                    }
                                }) { Text(if (person.id == snapshot.owner) "Mi estado · ${person.displayName}" else person.displayName) }
                            }
                        }
                        TextButton(onClick = { finish() }, enabled = !saving) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}
