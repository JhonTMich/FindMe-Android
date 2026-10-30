package com.example.findme.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.findme.data.local.dataStore
import com.example.findme.data.model.AccountProfile
import com.example.findme.data.repository.AccountRepository
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ConnectionWidgetSelection(val personId: String, val messageId: String)

object ConnectionWidgetSettings {
    suspend fun save(context: Context, ownerId: String, personId: String, messageId: String) {
        context.dataStore.edit {
            it[stringPreferencesKey("connection_widget_owner")] = ownerId
            it[stringPreferencesKey("connection_widget_person")] = personId
            it[stringPreferencesKey("connection_widget_message")] = messageId
        }
        ConnectionWidget().updateAll(context)
    }
    suspend fun read(context: Context, ownerId: String): ConnectionWidgetSelection? {
        val prefs = context.dataStore.data.first()
        if (prefs[stringPreferencesKey("connection_widget_owner")] != ownerId) return null
        val person = prefs[stringPreferencesKey("connection_widget_person")] ?: return null
        val message = prefs[stringPreferencesKey("connection_widget_message")] ?: return null
        return ConnectionWidgetSelection(person, message)
    }
}

class ConnectionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConnectionWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlanceWidgetWorker.enqueuePeriodicUpdate(context)
    }
}

private data class WidgetSnapshot(val mine: String, val theirs: String, val label: String?, val updated: String)

class ConnectionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = try {
            withTimeout(15_000) {
                val repo = AccountRepository()
                repo.auth.awaitInitialization()
                val owner = repo.userId()
                val selection = ConnectionWidgetSettings.read(context, owner)
                val profiles = repo.profiles()
                val mine = profiles.firstOrNull { it.id == owner }
                val theirs = profiles.firstOrNull { it.id == selection?.personId && it.id != owner }
                val message = selection?.let { config -> repo.quickMessages().find { it.id == config.messageId } }
                WidgetSnapshot(
                    mine?.let { "Tú: ${status(repo, it)}" } ?: "Abre FindMe para completar tu cuenta.",
                    theirs?.let { "${it.displayName}: ${status(repo, it)}" } ?: "Elige una persona y un botón en Personas.",
                    if (theirs != null) message?.label else null,
                    "Consultado ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}"
                )
            }
        } catch (_: TimeoutCancellationException) { WidgetSnapshot("Sin conexión reciente", "Pulsa Actualizar para reintentar.", null, "Consulta interrumpida") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { WidgetSnapshot("Información no disponible", "Abre FindMe o vuelve a actualizar.", null, "Sin datos recientes") }
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Color(0xFF10172A)).padding(14.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("FindMe · Mi gente", style = TextStyle(color = ColorProvider(Color(0xFFB7B0FF))))
                Spacer(GlanceModifier.height(8.dp))
                Text(snapshot.mine, style = TextStyle(color = ColorProvider(Color.White)))
                Text(snapshot.theirs, style = TextStyle(color = ColorProvider(Color.White)))
                Text(snapshot.updated, style = TextStyle(color = ColorProvider(Color(0xFFABB4C7))))
                Spacer(GlanceModifier.height(8.dp))
                snapshot.label?.let { Button(it, actionRunCallback<SendConnectionWidgetMessage>()) }
                Button("Actualizar", actionRunCallback<RefreshConnectionWidget>())
            }
        }
    }

    private suspend fun status(repo: AccountRepository, profile: AccountProfile): String {
        val now = ZonedDateTime.now(ZoneId.of(profile.timezone))
        return when (val state = StatusRepository.calculateStatus(profile.displayName, repo.schedules(profile.id), repo.overrides(profile.id), now.dayOfWeek.value, now, manualStatusFirst = true)) {
            is UserCurrentStatus.InClass -> "${state.currentSchedule.title} hasta ${state.currentSchedule.endTime.take(5)} (horario)"
            is UserCurrentStatus.Override -> state.customLocation
            else -> "Sin actividad programada"
        }
    }
}

class RefreshConnectionWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ConnectionWidget().updateAll(context)
    }
}

class SendConnectionWidgetMessage : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val feedback = try {
            withTimeout(15_000) {
                val repo = AccountRepository()
                repo.auth.awaitInitialization()
                val selection = requireNotNull(ConnectionWidgetSettings.read(context, repo.userId()))
                val message = repo.quickMessages().first { it.id == selection.messageId }
                repo.sendSignal(selection.personId, message.message)
                "Mensaje guardado. La otra persona puede consultarlo en FindMe."
            }
        } catch (_: TimeoutCancellationException) { "No se pudo confirmar el envío. Actualiza FindMe antes de reintentar." }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "No se pudo enviar. Revisa tu conexión y la configuración en FindMe." }
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, feedback, Toast.LENGTH_LONG).show() }
    }
}
