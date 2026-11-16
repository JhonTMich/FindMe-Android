package com.example.findme.data.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

object SupabaseClientProvider {
    const val SUPABASE_URL = "https://ymqsgxmpqushzghkoncg.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_zhBa9OeaMLNM94ZnGM0Xjw_Q5Z6jzu0"

    /** One OkHttp engine (connection pool, TLS sessions, threads) shared by every client. */
    private val engine: HttpClientEngine by lazy { OkHttp.create() }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = engine
            install(Postgrest)
            install(Auth)
            install(Realtime)
        }
    }

    /** For the Edge Functions called before a session exists (login and registration). */
    val functionsHttp: HttpClient by lazy {
        HttpClient(engine) {
            install(HttpTimeout) { requestTimeoutMillis = 45_000; connectTimeoutMillis = 15_000 }
        }
    }
}
