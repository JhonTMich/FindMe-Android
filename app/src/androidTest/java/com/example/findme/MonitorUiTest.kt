package com.example.findme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.UserSession
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.ui.components.HeroStatusCard
import com.example.findme.ui.components.WeeklyScheduleHeader
import com.example.findme.ui.screen.UpdateState
import com.example.findme.ui.screen.profile.ProfileScreen
import com.example.findme.ui.theme.FindmeTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class MonitorUiTest {
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    @Test fun profileShowsSettingsAndConfirmsUnlink() {
        var activityEnabled = true
        var statusEnabled = true
        compose.setContent { FindmeTheme { Surface(Modifier.background(com.example.findme.ui.theme.DarkBackground).safeDrawingPadding()) {
            ProfileScreen(UserSession("test", "Persona vinculada", "PRUEBA", "Mi cuenta"), true, true,
                { activityEnabled = it }, { statusEnabled = it }, UpdateState(), {}, {}, {})
        } } }
        screenshot("profile")
        compose.onNodeWithText("Inicio de actividades").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cambios de estado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Inicio de actividades").performScrollTo().performClick()
        compose.onNodeWithText("Cambios de estado").performScrollTo().performClick()
        org.junit.Assert.assertFalse(activityEnabled)
        org.junit.Assert.assertFalse(statusEnabled)
        compose.onNodeWithText("Desvincular cuenta").performScrollTo().performClick()
        compose.onNodeWithText("¿Desvincular cuenta?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
    }
    @Test fun freeTimeDoesNotClaimAConfirmedLocation() {
        compose.setContent { FindmeTheme { Surface(Modifier.fillMaxSize().background(com.example.findme.ui.theme.DarkBackground).safeDrawingPadding()) {
            Column(Modifier.padding(20.dp)) {
                HeroStatusCard(UserCurrentStatus.FreeTime("Persona vinculada", 1, emptyList()))
            }
        } } }
        compose.onNodeWithText("Sin actividad en curso").assertIsDisplayed()
        compose.onNodeWithText("Según el horario de hoy. La ubicación no está confirmada.").assertIsDisplayed()
        screenshot("today-status")
    }
    @Test fun weeklySelectorExposesAllSevenDays() {
        var selected = 1
        compose.setContent { FindmeTheme { Surface(Modifier.fillMaxSize().background(com.example.findme.ui.theme.DarkBackground).safeDrawingPadding()) {
            Column(Modifier.padding(20.dp)) {
                WeeklyScheduleHeader(emptyList<ScheduleDto>(), 1, { selected = it })
            }
        } } }
        compose.onNodeWithText("Tu semana").assertIsDisplayed()
        screenshot("weekly-schedule")
        compose.onAllNodes(hasClickAction()).onLast().performClick()
        org.junit.Assert.assertEquals(7, selected)
    }
    @Test fun activityCardShowsScheduleDetails() {
        val now = java.time.LocalTime.now()
        val activity = ScheduleDto(id = "visual", dayOfWeek = java.time.LocalDate.now().dayOfWeek.value,
            startTime = now.minusMinutes(30).toString(), endTime = now.plusMinutes(45).toString(),
            title = "Diseño y desarrollo de aplicaciones", location = "Aula 302 · Campus universitario", category = "university", modality = "Presencial")
        compose.setContent { FindmeTheme { Surface(Modifier.fillMaxSize().background(com.example.findme.ui.theme.DarkBackground).safeDrawingPadding()) {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { androidx.compose.material3.Text("Su día, a tu alcance", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = com.example.findme.ui.theme.TextPrimary) }
                item { HeroStatusCard(UserCurrentStatus.InClass("Alex", activity, 45, .6f, 1, listOf(activity))) }
                item { com.example.findme.ui.components.DayOverview(listOf(activity)) }
                item { com.example.findme.ui.components.ActivityTimelineCard(activity, active = true, last = true) }
            }
        } } }
        compose.onNodeWithText("Su día, a tu alcance").assertIsDisplayed()
        screenshot("today-designed")
        compose.onNodeWithText("Aula 302 · Campus universitario", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hasta ${activity.endTime.take(5)}").assertIsDisplayed()
    }

}
