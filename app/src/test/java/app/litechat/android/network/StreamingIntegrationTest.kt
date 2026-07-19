package app.litechat.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class StreamingIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenAiCompatibleAdapter

    @Before fun setUp() {
        val held = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        server = MockWebServer().apply { useHttps(serverCerts.sslSocketFactory(), false); start() }
        val client = OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build()
        adapter = OpenAiCompatibleAdapter(client)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun streamsTextAndCompletes() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n" +
                "data: [DONE]\n\n"
        ))
        val events = adapter.stream(request(), "test-key").toList()
        assertEquals("Hello", events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.text })
        assertTrue(events.last() is ChatEvent.Completed)
        val recorded = server.takeRequest()
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertFalse(recorded.body.readUtf8().contains("test-key"))
    }

    @Test fun mapsAuthenticationAndRateLimitWithoutRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid key"))
        val auth = runCatching { adapter.stream(request(), "bad").toList() }.exceptionOrNull() as ProviderException
        assertEquals(ProviderException.Category.AUTHENTICATION, auth.category)
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(429).setBody("quota"))
        val rate = runCatching { adapter.stream(request(), "bad").toList() }.exceptionOrNull() as ProviderException
        assertEquals(ProviderException.Category.RATE_LIMIT, rate.category)
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedEventIsActionable() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: not-json\n\n"))
        val error = runCatching { adapter.stream(request(), "key").toList() }.exceptionOrNull() as ProviderException
        assertEquals(ProviderException.Category.MALFORMED, error.category)
    }

    @Test fun cancellingCollectorImmediatelyCancelsAStalledNetworkStream() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val collector = launch {
            adapter.stream(request(), "key").collect { }
        }

        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        withTimeout(1_000) { collector.cancelAndJoin() }
        assertTrue(collector.isCancelled)
    }

    private fun request() = ChatRequest(
        baseUrl = server.url("/v1").toString().trimEnd('/'), model = "model", systemPrompt = "",
        messages = listOf(ChatInputMessage("user", "hello")), searchEnabled = false
    )
}
