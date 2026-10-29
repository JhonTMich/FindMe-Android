package com.example.findme

import com.example.findme.data.repository.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = "signed apk fixture".toByteArray()
    private val source = "https://github.com/JhonTMich/FindMe-Android/releases/download/v1.3/FindMe-1.3.apk"
    private fun apk(size: Long = bytes.size.toLong(), digest: String? = hash(bytes)) = ReleaseApk("FindMe-1.3.apk", source, size, digest)
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private class Connection(url: URL, private val stream: InputStream, private val code: Int = 200) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = stream
    }
    @Test fun downloadsChecksumsAndReportsProgress() = runBlocking {
        val directory = temporary.newFolder()
        lateinit var connection: Connection
        val downloader = ApkDownloader { Connection(it, ByteArrayInputStream(bytes)).also { value -> connection = value } }
        val progress = mutableListOf<Float>()
        val file = downloader.download(apk(), directory) { progress += it }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(1f, progress.last())
        assertTrue(connection.disconnected)
        assertTrue(directory.listFiles()!!.none { it.extension == "part" })
    }
    @Test fun truncatedOversizedAndCorruptDownloadsAreDiscarded() = runBlocking {
        for (metadata in listOf(apk(size = bytes.size + 1L), apk(size = bytes.size - 1L), apk(digest = "00".repeat(32)))) {
            val directory = temporary.newFolder()
            try {
                ApkDownloader { Connection(it, ByteArrayInputStream(bytes)) }.download(metadata, directory) { }
                fail("Invalid download accepted")
            } catch (_: IllegalStateException) { }
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }
    @Test fun networkFailuresDoNotLeavePartialApks() = runBlocking {
        val directory = temporary.newFolder()
        try {
            ApkDownloader { Connection(it, ByteArrayInputStream(bytes), 503) }.download(apk(), directory) { }
            fail("HTTP failure accepted")
        } catch (_: IOException) { }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun cancellationCleansUpThePartialFile() = runBlocking {
        val directory = temporary.newFolder()
        val input = object : InputStream() {
            override fun read(): Int = throw CancellationException("Cancelled")
        }
        try {
            ApkDownloader { Connection(it, input) }.download(apk(), directory) { }
            fail("Cancellation ignored")
        } catch (_: CancellationException) { }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
