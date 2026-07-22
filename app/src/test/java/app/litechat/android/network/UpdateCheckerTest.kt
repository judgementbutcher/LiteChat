package app.litechat.android.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class UpdateCheckerTest {
    @Test fun readsTheMatchingApkAndChecksumFromLatestRelease() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {
                  "tag_name": "v0.6.2",
                  "html_url": "https://github.com/judgementbutcher/LiteChat/releases/tag/v0.6.2",
                  "assets": [
                    {"name":"notes.txt","browser_download_url":"https://example.com/notes"},
                    {"name":"LiteChat-0.6.2-debug.apk","browser_download_url":"https://example.com/app.apk"},
                    {"name":"LiteChat-0.6.2-debug.apk.sha256","browser_download_url":"https://example.com/app.apk.sha256"}
                  ]
                }
            """.trimIndent()))
            server.start()

            val release = UpdateChecker(OkHttpClient(), server.url("/latest").toString()).check()

            assertEquals("v0.6.2", release.tag)
            assertEquals("https://example.com/app.apk", release.apkUrl)
            assertEquals("https://example.com/app.apk.sha256", release.checksumUrl)
            assertEquals("LiteChat/${app.litechat.android.BuildConfig.VERSION_NAME}", server.takeRequest().getHeader("User-Agent"))
        }
    }

    @Test fun fallsBackToPublicLatestPageWhenApiIsRateLimited() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("{\"message\":\"rate limit exceeded\"}"))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/releases/tag/v0.7.4"))
            server.enqueue(MockResponse().setBody("release"))
            server.start()

            val release = UpdateChecker(
                OkHttpClient(),
                server.url("/api/latest").toString(),
                server.url("/releases/latest").toString()
            ).check()

            assertEquals("v0.7.4", release.tag)
            assertEquals("https://github.com/judgementbutcher/LiteChat/releases/download/v0.7.4/LiteChat-0.7.4-debug.apk", release.apkUrl)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun comparesReleaseVersionsNumerically() {
        assertTrue(isNewerVersion("v0.6.10", "0.6.2"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
        assertFalse(isNewerVersion("v0.6.2", "0.6.2"))
        assertFalse(isNewerVersion("0.6.1", "0.6.2"))
        assertFalse(isNewerVersion("next", "0.6.2"))
    }
}
