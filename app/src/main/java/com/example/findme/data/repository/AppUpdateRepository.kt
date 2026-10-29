package com.example.findme.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val notes: String, val url: String)

object ReleaseVersion {
    private fun parts(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        if (!normalized.matches(Regex("\\d+(\\.\\d+)*"))) return null
        return normalized.split('.').map { it.toIntOrNull() ?: return null }
    }
    fun isNewer(candidate: String, installed: String): Boolean {
        val remote = parts(candidate) ?: return false
        val local = parts(installed) ?: return false
        for (index in 0 until maxOf(remote.size, local.size)) {
            val comparison = (remote.getOrElse(index) { 0 }).compareTo(local.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}

class AppUpdateRepository {
    suspend fun latest(installedVersion: String): AppRelease? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/JhonTMich/FindMe-Android/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "FindMe-Android/$installedVersion")
            val code = connection.responseCode
            check(code == 200) {
                if (code == 404) "No hay releases públicos disponibles todavía."
                else if (code == 403 || code == 429) "GitHub limitó las consultas. Intenta más tarde."
                else "No se pudo consultar GitHub (HTTP $code)."
            }
            val json = Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject
            if (json["draft"]?.jsonPrimitive?.booleanOrNull == true ||
                json["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return@withContext null
            val version = json.getValue("tag_name").jsonPrimitive.content
            val url = json.getValue("html_url").jsonPrimitive.content
            require(url.startsWith("https://github.com/JhonTMich/FindMe-Android/releases/"))
            if (ReleaseVersion.isNewer(version, installedVersion)) {
                AppRelease(version, json["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(4000), url)
            } else null
        } finally {
            connection.disconnect()
        }
    }
}
