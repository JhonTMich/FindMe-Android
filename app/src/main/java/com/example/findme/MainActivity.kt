package com.example.findme

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.findme.ui.screen.account.AccountScreen
import com.example.findme.ui.screen.account.AccountViewModel
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.ui.components.AppUpdateDialog
import com.example.findme.ui.screen.MainStatusScreen
import com.example.findme.ui.screen.MainViewModel
import com.example.findme.ui.screen.UpdateViewModel
import com.example.findme.ui.screen.pairing.PairingScreen
import com.example.findme.ui.screen.pairing.PairingViewModel
import com.example.findme.ui.theme.FindmeTheme
import com.example.findme.util.ApkUpdateInstaller
import com.example.findme.domain.model.ConfirmationLink
import androidx.compose.material3.AlertDialog
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.local.AppAccent
import com.example.findme.util.NotificationHelper

class MainActivity : ComponentActivity() {
    private var confirmationLink by mutableStateOf<ConfirmationLink?>(null)
    private var openPeople by mutableStateOf(false)
    private val appSettings by lazy { AccountSettingsRepository(applicationContext) }
    private val updateViewModel: UpdateViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val pairingViewModel: PairingViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val installPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) updateViewModel.requestInstallation()
        else updateViewModel.installationUnavailable()
    }
    private val installerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != RESULT_OK) updateViewModel.installationCancelled()
    }

    private fun installDownloadedUpdate() {
        val file = updateViewModel.state.value.downloadedApk ?: return
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                updateViewModel.permissionRequested()
                installPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
            } else {
                updateViewModel.installerOpened()
                installerLauncher.launch(ApkUpdateInstaller.intent(this, file))
            }
        } catch (_: ActivityNotFoundException) {
            updateViewModel.installationUnavailable()
        } catch (_: SecurityException) {
            updateViewModel.installationUnavailable()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveConfirmation(intent)
        com.example.findme.util.NotificationHelper.createNotificationChannel(this)
        enableEdgeToEdge()
        setContent {
            val accent by appSettings.accent.collectAsStateWithLifecycle(initialValue = AppAccent.Indigo)
            val accountState by accountViewModel.state.collectAsStateWithLifecycle()
            FindmeTheme(accent = accent) {
                val updateState by updateViewModel.state.collectAsStateWithLifecycle()
                // Defer the installer until this activity is visible; consume each request once.
                LifecycleResumeEffect(updateState.requestInstall) {
                    if (updateState.requestInstall) {
                        updateViewModel.consumeInstallRequest()
                        installDownloadedUpdate()
                    }
                    onPauseOrDispose { }
                }
                AppUpdateDialog(updateState, updateViewModel::update, updateViewModel::dismiss, updateViewModel::cancelDownload)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LaunchedEffect(Unit) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity,
                                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var legacy by rememberSaveable { mutableStateOf(false) }
                    confirmationLink?.let { link ->
                        AlertDialog(onDismissRequest = { confirmationLink = null },
                            title = { Text(if (link == ConfirmationLink.Failed) "Revisa tu confirmación" else "Continúa en FindMe") },
                            text = { Text(if (link == ConfirmationLink.Failed)
                                "El enlace no pudo completarse o ya venció. Solicita otro correo de confirmación."
                                else "El enlace se abrió en FindMe. Inicia sesión con el usuario y la contraseña que elegiste.") },
                            confirmButton = { TextButton(onClick = {
                                legacy = false
                                accountViewModel.finishRegistration()
                                confirmationLink = null
                            }) { Text("Continuar") } })
                    }
                    LaunchedEffect(openPeople, accountState.initializing) {
                        if (openPeople && !accountState.initializing) { legacy = false; accountViewModel.openPeople(); openPeople = false }
                    }
                    BackHandler(enabled = legacy) { legacy = false }
                    if (!legacy) {
                        AccountScreen(accountViewModel, onLegacy = { legacy = true }, updates = updateState, onCheckUpdates = updateViewModel::check, onInstallUpdate = updateViewModel::update)
                    } else {
                        Column {
                            TextButton(onClick = { legacy = false }, modifier = Modifier.statusBarsPadding()) { Text("Volver a cuentas") }
                            val userSession by mainViewModel.userSession.collectAsStateWithLifecycle()
                            if (userSession == null) {
                                PairingScreen(viewModel = pairingViewModel, onPairingSuccess = mainViewModel::refresh)
                            } else {
                                MainStatusScreen(viewModel = mainViewModel, updateState = updateState,
                                    onCheckUpdate = updateViewModel::check, onInstallUpdate = updateViewModel::update)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveConfirmation(intent)
        setIntent(intent)
    }

    private fun receiveConfirmation(intent: Intent?) {
        if (intent?.action == NotificationHelper.OPEN_PEOPLE) { openPeople = true; intent.action = null; return }
        if (intent?.action != Intent.ACTION_VIEW) return
        ConfirmationLink.parse(intent.dataString)?.let {
            confirmationLink = it
            // Do not retain callback fragments or tokens in the activity's Intent.
            intent.data = null
        }
    }
}
