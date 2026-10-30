package com.example.findme.data.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.cio.CIO

object SupabaseClientProvider {
    const val SUPABASE_URL = "https://ymqsgxmpqushzghkoncg.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_zhBa9OeaMLNM94ZnGM0Xjw_Q5Z6jzu0"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = CIO.create()
            install(Postgrest)
            install(Auth)
            install(Realtime)
        }
    }
}
