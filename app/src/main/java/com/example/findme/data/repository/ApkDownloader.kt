package com.example.findme.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ApkDownloader(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    suspend fun download(apk: ReleaseApk, directory: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        check(ReleaseApkPolicy.isTrustedUrl(apk.url)) { "La descarga de la actualización no es válida." }
        check(apk.size in 1..ReleaseApkPolicy.MAX_BYTES)
        check(directory.isDirectory || directory.mkdirs()) { "No hay espacio disponible para descargar la actualización." }
        val identifier = java.util.UUID.randomUUID().toString()
        val partial = File(directory, "$identifier.part")
        val destination = File(directory, "$identifier.apk")
        val connection = openConnection(URL(apk.url))
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "FindMe-Android")
            if (connection.responseCode != 200 || connection.url.protocol != "https") {
                throw IOException("No se pudo descargar la actualización.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        currentCoroutineContext().ensureActive()
                        received += count
                        check(received <= apk.size) { "La descarga de la actualización no es válida." }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val percent = (received * 100 / apk.size).toInt()
                        if (percent != lastPercent) {
                            onProgress(received.toFloat() / apk.size)
                            lastPercent = percent
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            check(received == apk.size) { "La descarga quedó incompleta. Intenta otra vez." }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            check(apk.sha256 == null || actualDigest == apk.sha256) { "No se pudo verificar la actualización. Intenta otra vez." }
            check(!destination.exists() || destination.delete())
            check(partial.renameTo(destination)) { "No se pudo guardar la actualización." }
            destination
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }
}
