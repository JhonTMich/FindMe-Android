package com.example.findme

import com.example.findme.data.repository.*
import org.junit.Assert.*
import org.junit.Test

class AppReleaseParserTest {
    private val repository = AppUpdateRepository()
    private val url = "https://github.com/JhonTMich/FindMe-Android/releases/download/v1.3/FindMe-1.3.apk"
    private fun asset(name: String = "FindMe-1.3.apk", source: String = url, size: Long = 100, digest: String = "\"sha256:${"ab".repeat(32)}\"") =
        """{"name":"$name","browser_download_url":"$source","size":$size,"digest":$digest,"state":"uploaded"}"""
    private fun release(assets: String, draft: Boolean = false, prerelease: Boolean = false) =
        """{"tag_name":"v1.3","body":"Patch notes are ignored","html_url":"https://github.com/JhonTMich/FindMe-Android/releases/tag/v1.3","draft":$draft,"prerelease":$prerelease,"assets":[$assets]}"""
    @Test fun selectsTheDirectApkInsteadOfARepositoryPage() {
        val result = repository.parseRelease(release(asset()), "1.2")!!
        assertEquals(url, result.apk.url)
        assertEquals("v1.3", result.version)
    }
    @Test fun ignoresDraftsPrereleasesAndInstalledVersions() {
        assertNull(repository.parseRelease(release(asset(), draft = true), "1.2"))
        assertNull(repository.parseRelease(release(asset(), prerelease = true), "1.2"))
        assertNull(repository.parseRelease(release(asset()), "1.3"))
    }
    @Test(expected = IllegalStateException::class) fun missingApkDoesNotOfferAnUnusableUpdate() {
        repository.parseRelease(release(""), "1.2")
    }
    @Test(expected = IllegalStateException::class) fun ambiguousArchitectureDownloadsAreRejected() {
        repository.parseRelease(release(asset("arm64.apk") + "," + asset("x86.apk")), "1.2")
    }
    @Test fun selectsUniversalApkWhenSeveralArchitecturesExist() {
        val result = repository.parseRelease(release(asset("arm64.apk") + "," + asset("universal.apk")), "1.2")!!
        assertEquals("universal.apk", result.apk.name)
    }
    @Test fun refusesUntrustedUrlsAndInvalidSizes() {
        for (source in listOf("http://github.com/JhonTMich/FindMe-Android/releases/download/v1.3/app.apk",
                "https://github.com.evil.test/JhonTMich/FindMe-Android/releases/download/v1.3/app.apk",
                "https://github.com/someone/another/releases/download/v1.3/app.apk")) {
            assertFalse(ReleaseApkPolicy.isTrustedUrl(source))
        }
        for (size in listOf(0L, ReleaseApkPolicy.MAX_BYTES + 1)) {
            try { repository.parseRelease(release(asset(size = size)), "1.2"); fail("Invalid size accepted") }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun acceptsOnlyValidSha256Digests() {
        val digest = "ab".repeat(32)
        assertEquals(digest, repository.parseRelease(release(asset(digest = "\"sha256:$digest\"")), "1.2")!!.apk.sha256)
        for (bad in listOf("\"sha256:bad\"", "null", "\"md5:${"ab".repeat(16)}\"", "\"${"ab".repeat(32)}\"")) {
            try { repository.parseRelease(release(asset(digest = bad)), "1.2"); fail("Missing or bad digest accepted: $bad") }
            catch (_: IllegalStateException) { }
        }
    }
}
