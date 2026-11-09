package com.example.findme

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
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

    private fun show(actions: AccountActions = object : AccountActions {}, snapshot: AccountState = state) {
        compose.setContent { FindmeTheme { AccountContent(snapshot, actions) } }
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

    @Test fun scheduleEditorBlocksConflictsAndAllowsVirtualClassDuringWork() {
        val otherDay = (now.dayOfWeek.value % 7) + 1
        val thirdDay = (otherDay % 7) + 1
        val work = schedule.copy(id = "work", dayOfWeek = otherDay, title = "Turno", category = "work")
        var savedTitle = ""
        var savedModality = ""
        show(object : AccountActions {
            override fun saveSchedules(draftId: String, activities: List<ScheduleDto>) {
                assertEquals(setOf(otherDay, thirdDay), activities.map { it.dayOfWeek }.toSet())
                assertEquals(2, activities.map { it.id }.distinct().size)
                activities.forEach {
                    savedTitle = it.title
                    savedModality = it.modality
                    assertEquals("university", it.category)
                    assertEquals("21:01", it.startTime)
                    assertEquals("22:00", it.endTime)
                }
            }
        }, snapshot = state.copy(schedules = listOf(schedule, work)))
        compose.onNodeWithText("Horario").performClick()
        compose.onNodeWithText("Tu semana").assertIsDisplayed()
        screenshot("account-schedule")
        compose.onNodeWithText("Añadir").performScrollTo().performClick()
        val heading = compose.onNodeWithText("Nueva actividad")
        repeat(3) {
            heading.performTouchInput { swipeDown(endY = bottom + 350f) }
            heading.assertIsDisplayed()
        }
        listOf("30 min", "1 h", "90 min").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("Título").performTextInput("Nuevo proyecto")
        compose.onNodeWithText("Guardar actividad").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Coincide con «Proyecto de investigación»", substring = true).assertExists()
        compose.onNodeWithText("Tipo").performScrollTo().performClick()
        compose.onNodeWithText("Trabajo").performClick()
        compose.onNodeWithText("Guardar actividad").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Tipo").performScrollTo().performClick()
        compose.onNode(hasText("Universidad") and hasClickAction()).performClick()
        // Use a work activity on a different day to check the allowed pairing.
        val dayLabels = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val dayField = compose.onNodeWithText("Días")
        dayField.performScrollTo().performClick()
        compose.onNode(hasText(dayLabels[otherDay - 1]) and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText(dayLabels[thirdDay - 1]) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Listo").performScrollTo().performClick()
        compose.onNodeWithText("Modalidad").performScrollTo().performClick()
        compose.onNodeWithText("Virtual").performClick()
        compose.onNodeWithText("Guardar actividad").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Coincide con «Proyecto de investigación» el ${dayLabels[now.dayOfWeek.value - 1]}",
            substring = true, ignoreCase = true).assertExists()
        dayField.performScrollTo().performClick()
        compose.onNode(hasText(dayLabels[now.dayOfWeek.value - 1]) and hasClickAction() and isSelected()).performScrollTo().performClick()
        compose.onNodeWithText("Listo").performScrollTo().performClick()
        compose.onNodeWithText("Inicio").performScrollTo().performClick()
        compose.onNodeWithText("Hora de inicio").assertIsDisplayed()
        compose.onNodeWithText("01").performClick()
        compose.onNodeWithText("p. m.").performClick()
        screenshot("account-schedule-wheel")
        compose.onNodeWithText("Aplicar hora").performClick()
        compose.onNodeWithText("9:01 p. m.").assertIsDisplayed()
        compose.onNodeWithText("Fin").performScrollTo().performClick()
        compose.onNodeWithText("Hora de fin").assertIsDisplayed()
        compose.onNodeWithText("p. m.").performClick()
        compose.onNodeWithText("Aplicar hora").performClick()
        screenshot("account-schedule-editor")
        compose.onNodeWithText("Guardar actividad").performScrollTo().performClick()
        assertEquals("Nuevo proyecto", savedTitle)
        assertEquals("virtual", savedModality)
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        heading.assertDoesNotExist()
    }

    @Test fun scheduleEditorIgnoresItsOwnSavedRowAndReusesIdOnRetry() {
        val snapshot = mutableStateOf(state.copy(schedules = emptyList()))
        val attempts = mutableListOf<String>()
        val actions = object : AccountActions {
            override fun saveSchedules(draftId: String, activities: List<ScheduleDto>) {
                val row = activities.single().copy(userId = own.id)
                attempts += row.id
                snapshot.value = if (attempts.size == 1) {
                    snapshot.value.copy(busy = true, schedules = listOf(row))
                } else {
                    snapshot.value.copy(busy = false, error = null, savedScheduleDraftId = draftId,
                        notice = "Actividad guardada.", schedules = listOf(row))
                }
            }
        }
        compose.setContent { FindmeTheme { AccountContent(snapshot.value, actions) } }
        compose.onNodeWithText("Horario").performClick()
        compose.onNodeWithText("Añadir").performScrollTo().performClick()
        compose.onNodeWithText("Título").performTextReplacement("Clase nueva")
        val save = compose.onNodeWithText("Guardar actividad")
        save.performSemanticsAction(SemanticsActions.OnClick)
        save.assertIsNotEnabled()
        compose.onNodeWithText("Coincide con «Clase nueva»", substring = true).assertDoesNotExist()

        compose.runOnIdle {
            snapshot.value = snapshot.value.copy(busy = false, error = "No se pudo confirmar el guardado.")
        }
        save.assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Nueva actividad").assertDoesNotExist()
        compose.onNodeWithText("Clase nueva").assertExists()
        assertEquals(2, attempts.size)
        assertEquals(attempts.first(), attempts.last())
    }

    @Test fun editorPreservesTheOvernightFlagAndRejectsMissingFlag() {
        val night = schedule.copy(id = "night", startTime = "19:00", endTime = "07:00",
            title = "Turno nocturno", category = "work", endsNextDay = true)
        var saved: ScheduleDto? = null
        show(object : AccountActions {
            override fun saveSchedules(draftId: String, activities: List<ScheduleDto>) {
                saved = activities.single()
            }
        }, snapshot = state.copy(schedules = listOf(night)))
        compose.onNodeWithText("Horario").performClick()
        compose.onNodeWithText("(+1 día)", substring = true).assertExists()
        compose.onNodeWithText("Editar").performScrollTo().performClick()
        val overnight = compose.onNodeWithText("Termina al día siguiente")
        overnight.assertIsOn().performScrollTo().performClick()
        compose.onNodeWithText("Guardar actividad").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertNull(saved)
        compose.onNodeWithText("Activa «Termina al día siguiente»", substring = true).assertExists()
        overnight.performScrollTo().performClick()
        compose.onNodeWithText("Guardar actividad").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("night", saved?.id)
        assertEquals("19:00", saved?.startTime)
        assertEquals("07:00", saved?.endTime)
        assertTrue(saved?.endsNextDay == true)
        screenshot("overnight-editor")
    }

    @Test fun todaysAgendaIncludesTheShiftThatStartedYesterday() {
        val night = schedule.copy(id = "night", dayOfWeek = (now.dayOfWeek.value + 5) % 7 + 1,
            startTime = "19:00", endTime = "07:00", title = "Turno nocturno",
            category = "work", endsNextDay = true)
        show(snapshot = state.copy(schedules = listOf(night), statuses = emptyList()))
        compose.onNodeWithText("Desde ayer").performScrollTo().assertIsDisplayed()
        screenshot("overnight-agenda")
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
