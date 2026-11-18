package com.example.findme.push

import android.util.Log
import com.example.findme.data.network.SupabaseClientProvider
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/** Links this device's FCM token to the signed-in account (or unlinks it on sign-out). Failures are not fatal. */
object PushTokens {
    private suspend fun currentToken(): String? = try {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Log.w("PushTokens", "No FCM token", error); null }

    suspend fun register(token: String? = null) {
        val value = token ?: currentToken() ?: return
        try {
            SupabaseClientProvider.client.postgrest.rpc("register_push_token", buildJsonObject { put("push_token", value) })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Log.w("PushTokens", "Token not registered", error) }
    }

    /** Call while the session still exists; also drops the local token so a new one is issued. */
    suspend fun unregister() {
        val value = currentToken() ?: return
        try {
            SupabaseClientProvider.client.postgrest.rpc("unregister_push_token", buildJsonObject { put("push_token", value) })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Log.w("PushTokens", "Token not unregistered", error) }
        runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }
}
