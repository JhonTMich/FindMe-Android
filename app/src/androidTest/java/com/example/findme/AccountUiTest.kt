package com.example.findme

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.model.*
import com.example.findme.ui.screen.account.*
import com.example.findme.ui.theme.FindmeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZonedDateTime

class AccountUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = ZonedDateTime.now(java.time.ZoneId.of("America/Lima"))
    private val own = AccountProfile("own", "Michael", username = "jhontmich")
    private val other = AccountProfile("other", "Ana", username = "ana")
    private val schedule = ScheduleDto("activity", "own", now.dayOfWeek.value, "00:00", "23:59", "Proyecto de investigación", "Campus · Aula 201")
    private val quick = QuickMessage("quick", "own", "¿Todo bien?", "¿Todo bien?")
    private val state = AccountState(initializing = false, signedIn = true, profile = own,
        schedules = listOf(schedule), statuses = listOf(StatusOverrideDto(id = "own", userId = "own", isActive = true,
            customLocation = "En camino a casa", expiresAt = now.plusHours(1).toInstant().toString())),
        connections = listOf(PersonConnection("connection", "own", "other")), profiles = listOf(own, other),
        selectedPerson = other, personSchedules = listOf(schedule.copy(userId = "other")), messages = listOf(quick),
        invitation = ConnectionInvitation("own", "ABCDEFGH", now.plusHours(24).toInstant().toString()))

    private fun show(actions: AccountActions = object : AccountActions {}) {
        compose.setContent { FindmeTheme { AccountContent(state, actions) } }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Capture the whole window, including modal sheets, and retain images after test cleanup.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FindMeTests")
        }))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun manualStatusIsVisibleDuringClassAndCanBeChanged() {
        var capturedText = ""
        var duration = 0L
        show(object : AccountActions {
            override fun setStatus(text: String, minutes: Long) { capturedText = text; duration = minutes }
        })
        compose.onNodeWithText("En camino a casa").assertIsDisplayed()
        screenshot("account-today")
        compose.onNodeWithText("Cambiar mi estado").performScrollTo().performClick()
        compose.onNodeWithText("Tu estado, ahora").assertIsDisplayed()
        compose.onNodeWithText("¿Qué estás haciendo?").performTextReplacement("Descansando")
        compose.onNodeWithText("30 min").performClick()
        compose.onNodeWithText("Compartir estado").performScrollTo().performClick()
        assertEquals("Descansando", capturedText)
        assertEquals(30L, duration)
    }

    @Test fun scheduleEditorKeepsCategoriesAndEditsAnActivity() {
        var savedTitle = ""
        show(object : AccountActions {
            override fun saveSchedule(existingId: String?, day: Int, start: String, end: String, title: String, location: String, category: String, modality: String) {
                savedTitle = title
                assertEquals("09:00", start)
                assertEquals("10:00", end)
            }
        })
        compose.onNodeWithText("Horario").performClick()
        compose.onNodeWithText("Tu semana").assertIsDisplayed()
        screenshot("account-schedule")
        compose.onNodeWithText("Añadir").performScrollTo().performClick()
        compose.onNodeWithText("Título").performTextInput("Nuevo proyecto")
        screenshot("account-schedule-editor")
        compose.onNodeWithText("Guardar actividad").performScrollTo().performClick()
        assertEquals("Nuevo proyecto", savedTitle)
    }

    @Test fun peopleExplainsMessagesAndKeepsConfigurationOutOfMainFlow() {
        var sent = ""
        show(object : AccountActions { override fun send(message: QuickMessage) { sent = message.id } })
        compose.onNodeWithText("Personas").performClick()
        compose.onNodeWithText("Tu gente").assertIsDisplayed()
        compose.onNodeWithText("Configura un botón").assertDoesNotExist()
        screenshot("account-people")
        compose.onNodeWithText("¿Todo bien?").performScrollTo().performClick()
        assertEquals("quick", sent)
        compose.onNodeWithText("Mis mensajes y widget").performScrollTo().performClick()
        compose.onNodeWithText("Tus mensajes rápidos").assertIsDisplayed()
        compose.onNodeWithText("¿Qué quieres decir?").assertIsDisplayed()
        compose.onNodeWithText("Nombre del botón").assertDoesNotExist()
        screenshot("account-messages")
    }

    @Test fun invitationHasShortReadableCodeAndSeparateJoinFlow() {
        var accepted = ""
        show(object : AccountActions { override fun accept(code: String) { accepted = code } })
        compose.onNodeWithText("Personas").performClick()
        compose.onNodeWithText("Conectar con alguien").performClick()
        compose.onNodeWithText("ABCD-EFGH").assertIsDisplayed()
        screenshot("account-invitation")
        compose.onNodeWithText("Tengo un código").performClick()
        compose.onNodeWithText("Código de invitación").performTextInput("abcd-efgh")
        compose.onNodeWithText("Conectar y compartir").performClick()
        assertEquals("abcd-efgh", accepted)
    }
    @Test fun unnamedAccountMustCompleteProfileBeforeOpeningSchedules() {
        var saved = ""
        compose.setContent { FindmeTheme {
            AccountContent(state.copy(profile = own.copy(displayName = "Mi cuenta", username = null)), object : AccountActions {
                override fun saveProfile(name: String, username: String) { saved = name }
            })
        } }
        compose.onNodeWithText("Completa tu perfil").assertIsDisplayed()
        compose.onNodeWithText("Horario").assertDoesNotExist()
        compose.onNodeWithText("Guardar y continuar").assertIsNotEnabled()
        compose.onNodeWithText("Nombre visible").performTextInput("  Ana  ")
        compose.onNodeWithText("Guardar y continuar").assertIsNotEnabled()
        compose.onNodeWithText("Nombre de usuario").performTextInput("ana_123")
        compose.onNodeWithText("Guardar y continuar").performScrollTo().performClick()
        assertEquals("Ana", saved)
        screenshot("account-complete-profile")
    }

    @Test fun ownNameCanBeEditedFromAccount() {
        var saved = ""
        show(object : AccountActions { override fun saveProfile(name: String, username: String) { saved = name } })
        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("Guardar perfil").assertIsNotEnabled()
        compose.onNodeWithText("Nombre visible").performTextReplacement("Miguel")
        compose.onNodeWithText("Guardar perfil").performScrollTo().performClick()
        assertEquals("Miguel", saved)
        screenshot("account-edit-profile")
    }

    @Test fun loginDoesNotOfferPublicSignup() {
        compose.setContent { FindmeTheme { AccountContent(AccountState(initializing = false), object : AccountActions {}) } }
        compose.onNodeWithText("Inicia sesión").assertIsDisplayed()
        compose.onNodeWithText("Nombre de usuario").assertIsDisplayed()
        compose.onNodeWithText("Correo electrónico").assertDoesNotExist()
        compose.onNodeWithText("Crear cuenta").assertDoesNotExist()
        compose.onNodeWithText("¿Primera vez? Crea tu cuenta").assertDoesNotExist()
    }

}
