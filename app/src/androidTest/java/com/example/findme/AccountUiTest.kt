package com.example.findme

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
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
    private fun screenshot(name: String, composeOnly: Boolean = false) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Capture the whole window, including modal sheets, and retain images after test cleanup.
        val bitmap = if (composeOnly) compose.onRoot().captureToImage().asAndroidBitmap() else requireNotNull(instrumentation.uiAutomation.takeScreenshot())
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
        compose.onNodeWithText("Perfil").performClick()
        compose.onNodeWithText("Guardar perfil").assertIsNotEnabled()
        compose.onNodeWithText("Nombre visible").performTextReplacement("Miguel")
        compose.onNodeWithText("Guardar perfil").performScrollTo().performClick()
        assertEquals("Miguel", saved)
        screenshot("account-edit-profile")
    }

    @Test fun loginUsesOnlyUsernameAndOffersAuthorizedRegistration() {
        compose.setContent { FindmeTheme { AccountContent(AccountState(initializing = false), object : AccountActions {}) } }
        compose.onNodeWithText("Inicia sesión").assertIsDisplayed()
        compose.onNodeWithText("Nombre de usuario").assertIsDisplayed()
        compose.onNodeWithText("Correo electrónico").assertDoesNotExist()
        compose.onNodeWithText("Crear cuenta").assertIsDisplayed()
        compose.onNodeWithText("¿Primera vez? Crea tu cuenta").assertDoesNotExist()
    }

    @Test fun registrationRequiresMatchingPasswordsAndSubmitsUserChoices() {
        var submitted = false
        compose.setContent { FindmeTheme {
            AccountContent(AccountState(initializing = false), object : AccountActions {
                override fun register(name: String, email: String, username: String, password: String, confirmation: String) {
                    assertEquals("Chosen Name", name)
                    assertEquals("signup@example.test", email)
                    assertEquals("chosen_user", username)
                    assertEquals("local-test-password", password)
                    assertEquals(password, confirmation)
                    submitted = true
                }
            })
        } }
        compose.onNodeWithText("Crear cuenta").performClick()
        compose.onNodeWithText("Nombre visible").performTextInput("Chosen Name")
        compose.onNodeWithText("Correo electrónico").performTextInput("signup@example.test")
        compose.onNodeWithText("Nombre de usuario").performTextInput("chosen_user")
        compose.onNodeWithText("Contraseña").performScrollTo().performTextInput("local-test-password")
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().performTextInput("wrong-password")
        compose.onNodeWithText("Registrarme").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().performTextReplacement("local-test-password")
        compose.onNodeWithText("Registrarme").performScrollTo().performClick()
        assertTrue(submitted)
    }

    @Test fun confirmationExplainsNextStepAndSupportsResending() {
        var resent = false
        var finished = false
        compose.setContent { FindmeTheme {
            AccountContent(AccountState(initializing = false, confirmationEmail = "signup@example.test"), object : AccountActions {
                override fun resendConfirmation(email: String) { assertEquals("signup@example.test", email); resent = true }
                override fun finishRegistration() { finished = true }
            })
        } }
        compose.onNodeWithText("Revisa tu correo").assertIsDisplayed()
        compose.onNodeWithText("Contraseña").assertDoesNotExist()
        compose.onNodeWithText("Reenviar confirmación").performScrollTo().performClick()
        assertTrue(resent)
        compose.onNodeWithText("Ya confirmé, iniciar sesión").performScrollTo().performClick()
        assertTrue(finished)
    }

    @Test fun profileRestoresSettingsAndChangesAccent() {
        val current = androidx.compose.runtime.mutableStateOf(state)
        compose.setContent {
            FindmeTheme(accent = current.value.settings.accent) {
                AccountContent(current.value, object : AccountActions {
                    override fun setAlert(alert: com.example.findme.data.local.AccountAlert, enabled: Boolean) {
                        assertEquals(com.example.findme.data.local.AccountAlert.Messages, alert)
                        current.value = current.value.copy(settings = current.value.settings.copy(messages = enabled))
                    }
                    override fun setAccent(accent: com.example.findme.data.local.AppAccent) {
                        current.value = current.value.copy(settings = current.value.settings.copy(accent = accent))
                    }
                })
            }
        }
        compose.onNodeWithText("Perfil").performClick()
        compose.onNodeWithText("Mensajes recibidos").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Mensajes recibidos").assertIsOff()
        screenshot("account-profile-notifications-compose", composeOnly = true)
        compose.onNodeWithText("Menta").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Menta").assertIsSelected()
        assertEquals(com.example.findme.data.local.AppAccent.Mint, current.value.settings.accent)
        screenshot("account-profile-mint-compose", composeOnly = true)
        compose.onNodeWithText("Buscar actualizaciones").performScrollTo().assertIsDisplayed()
    }

    @Test fun incomingMessageAppearsWithoutPressingRefresh() {
        val current = androidx.compose.runtime.mutableStateOf(state)
        compose.setContent { FindmeTheme { AccountContent(current.value, object : AccountActions {}) } }
        compose.onNodeWithText("Personas").performClick()
        compose.onNodeWithText("Te han escrito").assertDoesNotExist()
        compose.runOnIdle {
            current.value = current.value.copy(signals = listOf(ConnectionSignal(
                "local-message", other.id, own.id, "Mensaje de prueba local", java.time.Instant.now().toString()
            )))
        }
        compose.onNodeWithText("Te han escrito").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mensaje de prueba local").assertIsDisplayed()
        compose.onNodeWithText("Mensajes recientes · Últimas 24 horas").assertIsDisplayed()
    }

}
