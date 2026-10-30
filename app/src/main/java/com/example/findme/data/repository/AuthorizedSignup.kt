package com.example.findme.data.repository

import com.example.findme.data.network.SupabaseClientProvider
import com.example.findme.domain.model.Username
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*
import java.time.ZoneId
import java.util.Locale

internal object AuthorizedSignup {
    private val http = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 45_000; connectTimeoutMillis = 15_000 }
    }
    suspend fun register(name: String, email: String, username: String, password: String) = submit(buildJsonObject {
        put("email", email.trim().lowercase(Locale.ROOT)); put("name", name.trim())
        put("username", Username.normalize(username)); put("password", password)
        put("timezone", ZoneId.systemDefault().id)
    })
    suspend fun resend(email: String) = submit(buildJsonObject { put("email", email.trim().lowercase(Locale.ROOT)); put("action", "resend") })

    private suspend fun submit(body: JsonObject) {
        val response = http.post("${SupabaseClientProvider.SUPABASE_URL}/functions/v1/authorized-signup") {
            contentType(ContentType.Application.Json)
            header("apikey", SupabaseClientProvider.SUPABASE_ANON_KEY)
            setBody(body.toString())
        }
        val raw = response.bodyAsText()
        val result = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        if (response.status.value != 200 || result?.get("needs_confirmation")?.jsonPrimitive?.booleanOrNull != true) {
            val message = result?.get("message")?.jsonPrimitive?.contentOrNull
            throw IllegalArgumentException(message ?: "No se pudo completar el registro. Inténtalo más tarde.")
        }
    }
}
