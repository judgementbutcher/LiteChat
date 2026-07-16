package app.litechat.android.network

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AdapterRequestTest {
    private val client = OkHttpClient()
    private val base = ChatRequest(
        baseUrl = "https://example.com/v1", model = "test-model", systemPrompt = "Be concise",
        messages = listOf(ChatInputMessage("user", "Hello")), searchEnabled = true
    )

    @Test fun openAiResponsesMapsInstructionsInputAndSearch() {
        val body = OpenAiResponsesAdapter(client).body(base)
        assertEquals("test-model", body["model"]?.jsonPrimitive?.content)
        assertEquals("Be concise", body["instructions"]?.jsonPrimitive?.content)
        assertEquals("web_search", body["tools"]?.jsonArray?.first()?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test fun compatibleMapsSystemAndMessages() {
        val body = OpenAiCompatibleAdapter(client).body(base.copy(searchEnabled = false))
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages.first().jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("Hello", messages.last().jsonObject["content"]?.jsonPrimitive?.content)
    }

    @Test fun anthropicMapsImageAndNativeSearch() {
        val image = File.createTempFile("litechat", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val request = base.copy(messages = listOf(ChatInputMessage("user", "look", listOf(ChatAttachment("x.png", "image/png", image.absolutePath)))))
            val body = AnthropicAdapter(client).body(request)
            assertEquals("image", body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[1].jsonObject["type"]?.jsonPrimitive?.content)
            assertEquals("web_search_20250305", body["tools"]!!.jsonArray[0].jsonObject["type"]?.jsonPrimitive?.content)
        } finally { image.delete() }
    }

    @Test fun geminiMapsGroundingAndRoles() {
        val body = GeminiAdapter(client).body(base.copy(messages = listOf(ChatInputMessage("assistant", "Prior"), ChatInputMessage("user", "Next"))))
        assertEquals("model", body["contents"]!!.jsonArray[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertNotNull(body["tools"]!!.jsonArray[0].jsonObject["google_search"])
    }
}
