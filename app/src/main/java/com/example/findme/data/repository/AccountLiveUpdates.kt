package com.example.findme.data.repository

import com.example.findme.data.network.SupabaseClientProvider
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

/** Only collected while the account UI is visible. Event payloads never bypass RLS reads. */
internal object AccountLiveUpdates {
    fun observe(userId: String): Flow<Unit> = channelFlow {
        trySend(Unit)
        launch {
            while (isActive) { delay(60_000); trySend(Unit) }
        }
        launch {
            while (isActive) {
                val client = SupabaseClientProvider.client
                val channel = client.realtime.channel("account-$userId-${UUID.randomUUID()}") {}
                try {
                    coroutineScope {
                        listOf("connection_signals", "personal_schedule", "personal_status", "person_connections", "account_profiles", "quick_messages").forEach { source ->
                            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = source }
                            launch { changes.collect { trySend(Unit) } }
                        }
                        channel.subscribe(blockUntilSubscribed = true)
                        // Close the snapshot/subscription race, including a reconnect.
                        trySend(Unit)
                        awaitCancellation()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Bounded polling continues when the socket is unavailable. */ }
                finally {
                    withContext(NonCancellable) { withTimeoutOrNull(2_000) { client.realtime.removeChannel(channel) } }
                }
                delay(5_000)
            }
        }
    }.buffer(Channel.CONFLATED)
}
