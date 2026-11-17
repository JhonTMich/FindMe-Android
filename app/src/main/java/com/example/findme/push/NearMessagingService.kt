package com.example.findme.push

import android.app.ActivityManager
import android.content.Context
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.network.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import com.example.findme.util.NotificationHelper
import com.example.findme.widget.WidgetStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Receives the pushes sent by the send-push function, also while Near is closed. */
class NearMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            val auth = SupabaseClientProvider.client.auth
            auth.awaitInitialization()
            if (auth.currentUserOrNull() != null) PushTokens.register(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Runs on a background thread with a short time budget.
        runBlocking { withTimeoutOrNull(8_000) { show(applicationContext, message.data) } }
    }

    companion object {
        /** While Near is on screen its own refresh already notifies; skip the push so nothing shows twice. */
        private fun visible(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }

        suspend fun show(context: Context, data: Map<String, String>) {
            if (visible()) return
            val alert = PushAlerts.from(data) ?: return
            // The widget cache holds the signed-in account and is cleared on sign-out.
            val owner = WidgetStore.data(context)?.owner ?: return
            val settings = AccountSettingsRepository(context).settings(owner).first()
            if (!PushAlerts.allowed(alert, settings)) return
            NotificationHelper.show(context, alert.channel, alert.id, alert.title, alert.text)
        }
    }
}
