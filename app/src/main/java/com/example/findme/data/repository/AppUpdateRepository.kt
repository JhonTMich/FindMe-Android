package com.example.findme.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseApk(val name: String, val url: String, val size: Long, val sha256: String?)
data class AppRelease(val version: String, val apk: ReleaseApk)

object ReleaseApkPolicy {
    const val MAX_BYTES = 150L * 1024 * 1024
    fun isTrustedUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.path.startsWith("/JhonTMich/FindMe-Android/releases/download/")
    }.getOrDefault(false)
}

object ReleaseVersion {
    private fun parts(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        if (!normalized.matches(Regex("\\d+(\\.\\d+)*"))) return null
        return normalized.split('.').map { it.toIntOrNull() ?: return null }
    }
    fun equivalent(first: String, second: String): Boolean = compare(first, second) == 0
    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed)?.let { it > 0 } ?: false
    private fun compare(candidate: String, installed: String): Int? {
        val remote = parts(candidate) ?: return null
        val local = parts(installed) ?: return null
        for (index in 0 until maxOf(remote.size, local.size)) {
            val comparison = (remote.getOrElse(index) { 0 }).compareTo(local.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
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
            parseRelease(connection.inputStream.bufferedReader().use { it.readText() }, installedVersion)
        } finally {
            connection.disconnect()
        }
    }
    internal fun parseRelease(body: String, installedVersion: String): AppRelease? {
        val json = Json.parseToJsonElement(body).jsonObject
        if (json["draft"]?.jsonPrimitive?.booleanOrNull == true ||
            json["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
        val version = json.getValue("tag_name").jsonPrimitive.content
        if (!ReleaseVersion.isNewer(version, installedVersion)) return null
        val assets = json["assets"]?.jsonArray.orEmpty().map { it.jsonObject }.filter {
            it["name"]?.jsonPrimitive?.content.orEmpty().endsWith(".apk", ignoreCase = true) &&
                it["state"]?.jsonPrimitive?.content == "uploaded"
        }
        val preferred = assets.filter {
            val name = it.getValue("name").jsonPrimitive.content
            name.equals("FindMe-${version.removePrefix("v").removePrefix("V")}.apk", true) ||
                name.contains("universal", true)
        }
        val asset = (if (preferred.size == 1) preferred.single() else assets.singleOrNull())
            ?: error("La actualización aún no tiene un APK compatible disponible.")
        val url = asset.getValue("browser_download_url").jsonPrimitive.content
        check(ReleaseApkPolicy.isTrustedUrl(url)) { "No se pudo verificar la descarga de la actualización." }
        val size = asset.getValue("size").jsonPrimitive.long
        check(size in 1..ReleaseApkPolicy.MAX_BYTES) { "El tamaño de la actualización no es válido." }
        val digest = asset["digest"]?.jsonPrimitive?.contentOrNull
        val sha256 = digest?.removePrefix("sha256:")
        check(sha256 == null || (digest?.startsWith("sha256:") == true && sha256.matches(Regex("[0-9a-fA-F]{64}")))) {
            "No se pudo verificar la actualización."
        }
        return AppRelease(version, ReleaseApk(asset.getValue("name").jsonPrimitive.content, url, size, sha256?.lowercase()))
    }

}
