package com.example.findme.data.repository

import com.example.findme.data.network.SupabaseClientProvider
import com.example.findme.domain.model.Username
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*

internal object UsernameLogin {
    private val http = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 45_000; connectTimeoutMillis = 15_000 }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun signIn(username: String, password: String): UserSession {
        require(Username.isValid(username) && password.isNotBlank()) { "Introduce tu usuario y contraseña." }
        val response = http.post("${SupabaseClientProvider.SUPABASE_URL}/functions/v1/username-login") {
            contentType(ContentType.Application.Json)
            header("apikey", SupabaseClientProvider.SUPABASE_ANON_KEY)
            setBody(buildJsonObject { put("username", Username.normalize(username)); put("password", password) }.toString())
        }
        when (response.status.value) {
            200 -> return json.decodeFromString<UserSession>(response.bodyAsText())
            400, 401 -> throw IllegalArgumentException("Usuario o contraseña incorrectos.")
            429 -> throw IllegalArgumentException("Demasiados intentos. Espera unos minutos y vuelve a intentarlo.")
            else -> throw IllegalArgumentException("No se pudo iniciar sesión. Inténtalo de nuevo más tarde.")
        }
    }
}
