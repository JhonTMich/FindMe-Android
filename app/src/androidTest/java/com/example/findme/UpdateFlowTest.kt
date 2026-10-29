package com.example.findme

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.repository.AppRelease
import com.example.findme.data.repository.ReleaseApk
import com.example.findme.ui.components.AppUpdateDialog
import com.example.findme.ui.screen.UpdateState
import com.example.findme.ui.theme.FindmeTheme
import com.example.findme.util.ApkUpdateInstaller
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UpdateFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun oneDialogOffersUpdateAndShowsDownloadProgressWithoutPatchNotes() {
        var state by mutableStateOf(UpdateState(release = AppRelease("v1.3", ReleaseApk("FindMe-1.3.apk",
            "https://github.com/JhonTMich/FindMe-Android/releases/download/v1.3/FindMe-1.3.apk", 100, null)),
            message = "Hay una actualización de FindMe disponible.", showDialog = true))
        compose.setContent { FindmeTheme {
            AppUpdateDialog(state, onUpdate = { state = state.copy(downloading = true, progress = .42f, message = "Descargando actualización…") },
                onLater = { state = state.copy(showDialog = false) }, onCancelDownload = { state = state.copy(showDialog = false) })
        } }
        compose.onNodeWithText("Actualizar").performClick()
        compose.onNodeWithText("Actualizando FindMe").assertIsDisplayed()
        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithText("Actualización disponible").assertDoesNotExist()
        compose.onNodeWithText("Ver actualización").assertDoesNotExist()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Actualizando FindMe").assertDoesNotExist()
    }
    @Test fun validatesThePackageVersionAndCertificateBeforeInstallation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apk = File(context.applicationInfo.sourceDir)
        ApkUpdateInstaller.validate(context, apk, BuildConfig.VERSION_NAME, installedVersionCode = 0)
        try {
            ApkUpdateInstaller.validate(context, apk, BuildConfig.VERSION_NAME)
            fail("Same version code must not be installed as a new update")
        } catch (_: IllegalStateException) { }
        try {
            ApkUpdateInstaller.validate(context, apk, "99.0", installedVersionCode = 0)
            fail("Mismatched release version accepted")
        } catch (_: IllegalStateException) { }
        val otherPackage = File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir)
        try {
            ApkUpdateInstaller.validate(context, otherPackage, BuildConfig.VERSION_NAME, installedVersionCode = 0)
            fail("A different package was accepted")
        } catch (_: IllegalStateException) { }
    }
    @Test fun installerGetsAReadOnlyContentUriAndCannotShareOtherFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(directory, "intent-test.apk").apply { writeText("test") }
        val outside = File(context.cacheDir, "private-test.txt").apply { writeText("private") }
        try {
            val intent = ApkUpdateInstaller.intent(context, file)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.updates", intent.data?.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals("test", context.contentResolver.openInputStream(intent.data!!)!!.bufferedReader().use { it.readText() })
            try {
                ApkUpdateInstaller.intent(context, outside)
                fail("Provider shared a file outside the updates directory")
            } catch (_: IllegalArgumentException) { }
        } finally { file.delete(); outside.delete() }
    }
}
