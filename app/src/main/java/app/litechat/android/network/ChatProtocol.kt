package app.litechat.android.network

import app.litechat.android.data.model.ProtocolKind
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ChatAttachment(
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val extractedText: String? = null,
    val truncated: Boolean = false
)

data class ChatInputMessage(
    val role: String,
    val content: String,
    val attachments: List<ChatAttachment> = emptyList()
)

data class ChatRequest(
    val baseUrl: String,
    val model: String,
    val systemPrompt: String,
    val messages: List<ChatInputMessage>,
    val temperature: Float = 0.7f,
    val topP: Float = 1f,
    val searchEnabled: Boolean = false
)

sealed interface ChatEvent {
    data class TextDelta(val text: String) : ChatEvent
    data class SearchActivity(val summary: String) : ChatEvent
    data object Completed : ChatEvent
}

class ProviderException(
    val category: Category,
    override val message: String,
    val statusCode: Int? = null
) : Exception(message) {
    enum class Category { AUTHENTICATION, RATE_LIMIT, CONTEXT_LENGTH, UNSUPPORTED, NETWORK, SERVER, MALFORMED }
}

interface ChatAdapter {
    val protocol: ProtocolKind
    fun stream(request: ChatRequest, apiKey: String): Flow<ChatEvent>
}

object ProviderErrorMapper {
    fun fromHttp(code: Int, body: String): ProviderException {
        val normalized = body.lowercase()
        val category = when {
            code == 401 || code == 403 -> ProviderException.Category.AUTHENTICATION
            code == 429 -> ProviderException.Category.RATE_LIMIT
            code == 400 && ("context" in normalized || "token" in normalized) -> ProviderException.Category.CONTEXT_LENGTH
            code == 400 && ("unsupported" in normalized || "not support" in normalized) -> ProviderException.Category.UNSUPPORTED
            code >= 500 -> ProviderException.Category.SERVER
            else -> ProviderException.Category.MALFORMED
        }
        val guidance = when (category) {
            ProviderException.Category.AUTHENTICATION -> "Authentication failed. Check the API key and provider URL."
            ProviderException.Category.RATE_LIMIT -> "Rate limited or quota exhausted. Check provider billing and try later."
            ProviderException.Category.CONTEXT_LENGTH -> "The conversation is too long for this model. Remove older messages or attachments."
            ProviderException.Category.UNSUPPORTED -> "This model or provider does not support the requested capability."
            ProviderException.Category.SERVER -> "The provider is temporarily unavailable."
            else -> "The provider rejected the request."
        }
        val providerDetail = runCatching {
            val root = wireJson.parseToJsonElement(body).jsonObject
            root["error"]?.let { error ->
                runCatching { error.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                    ?: runCatching { error.jsonPrimitive.content }.getOrNull()
            } ?: root["message"]?.jsonPrimitive?.content
        }.getOrNull()?.trim()?.take(500)
        val message = providerDetail
            ?.takeIf { it.isNotBlank() && !guidance.contains(it, ignoreCase = true) }
            ?.let { "$guidance\n\nProvider response: $it" }
            ?: guidance
        return ProviderException(category, message, code)
    }
}
