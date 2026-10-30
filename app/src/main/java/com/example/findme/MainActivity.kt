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

class MainActivity : ComponentActivity() {
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
        com.example.findme.util.NotificationHelper.createNotificationChannel(this)
        enableEdgeToEdge()
        setContent {
            FindmeTheme {
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
                    BackHandler(enabled = legacy) { legacy = false }
                    if (!legacy) {
                        AccountScreen(accountViewModel, onLegacy = { legacy = true })
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
}
