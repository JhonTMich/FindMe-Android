package com.example.findme.data.repository

import com.example.findme.data.network.SupabaseClientProvider
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*

/** Calls the delete-account Edge Function, which re-checks the password before deleting. */
internal object AccountDeletion {
    private val http get() = SupabaseClientProvider.functionsHttp

    suspend fun delete(accessToken: String, password: String) {
        require(password.isNotEmpty()) { "Introduce tu contraseña para confirmar." }
        val response = http.post("${SupabaseClientProvider.SUPABASE_URL}/functions/v1/delete-account") {
            contentType(ContentType.Application.Json)
            header("apikey", SupabaseClientProvider.SUPABASE_ANON_KEY)
            bearerAuth(accessToken)
            setBody(buildJsonObject { put("password", password) }.toString())
        }
        if (response.status.value == 200) return
        val message = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        throw IllegalArgumentException(message ?: "No se pudo eliminar la cuenta. Inténtalo más tarde.")
    }
}
