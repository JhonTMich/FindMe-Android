package com.example.findme

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.ui.screen.MainStatusScreen
import com.example.findme.ui.screen.MainViewModel
import com.example.findme.ui.screen.pairing.PairingScreen
import com.example.findme.ui.screen.pairing.PairingViewModel
import com.example.findme.ui.theme.FindmeTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val pairingViewModel: PairingViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.findme.util.NotificationHelper.createNotificationChannel(this)
        
        enableEdgeToEdge()
        setContent {
            FindmeTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val userSession by mainViewModel.userSession.collectAsStateWithLifecycle()

                    if (userSession == null) {
                        PairingScreen(
                            viewModel = pairingViewModel,
                            onPairingSuccess = {
                                mainViewModel.refresh()
                            }
                        )
                    } else {
                        MainStatusScreen(viewModel = mainViewModel)
                    }
                }
            }
        }
    }
}
