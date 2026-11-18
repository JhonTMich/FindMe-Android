package com.example.findme.push

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
        @Volatile
        internal var accountScreenVisible = false
            private set

        internal suspend fun observeAccountScreen(observe: suspend () -> Unit) {
            accountScreenVisible = true
            try {
                observe()
            } finally {
                accountScreenVisible = false
            }
        }

        suspend fun show(context: Context, data: Map<String, String>) {
            if (accountScreenVisible) return
            val alert = PushAlerts.from(data) ?: return
            val auth = SupabaseClientProvider.client.auth
            auth.awaitInitialization()
            val owner = auth.currentUserOrNull()?.id ?: return
            if (owner != data["recipient_id"]) return
            val people = WidgetStore.data(context)?.takeIf { it.owner == owner }?.profiles.orEmpty()
            if (people.none { it.id == data["user_id"] }) return
            val settings = AccountSettingsRepository(context).settings(owner).first()
            if (auth.currentUserOrNull()?.id != owner || !PushAlerts.allowed(alert, settings)) return
            NotificationHelper.show(context, alert.channel, alert.id, alert.title, alert.text)
        }
    }
}
