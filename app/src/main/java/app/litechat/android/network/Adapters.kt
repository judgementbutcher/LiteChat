package app.litechat.android.network

import app.litechat.android.data.model.ProtocolKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.Base64

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
internal val wireJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private fun endpoint(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

private fun attachmentText(message: ChatInputMessage): String = buildString {
    append(message.content)
    message.attachments.filter { !it.mimeType.startsWith("image/") }.forEach { item ->
        item.extractedText?.let {
            append("\n\n--- Attachment: ${item.displayName}${if (item.truncated) " (truncated)" else ""} ---\n")
            append(it)
        }
    }
}

private fun imageDataUrl(item: ChatAttachment): String {
    val bytes = File(item.localPath).readBytes()
    return "data:${item.mimeType};base64,${Base64.getEncoder().encodeToString(bytes)}"
}

abstract class BaseAdapter(client: OkHttpClient) : ChatAdapter {
    protected val sse = SseClient(client)

    protected fun request(url: String, apiKey: String, body: JsonObject, headers: Map<String, String> = emptyMap()): Request {
        require(url.startsWith("https://")) { "Only HTTPS provider URLs are allowed." }
        return Request.Builder().url(url).post(body.toString().toRequestBody(JSON_MEDIA)).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Accept", "text/event-stream")
            header("Cache-Control", "no-store")
            if (apiKey.isNotBlank() && headers.keys.none { it.equals("Authorization", true) || it.equals("x-api-key", true) }) {
                header("Authorization", "Bearer $apiKey")
            }
        }.build()
    }
}

class OpenAiResponsesAdapter(client: OkHttpClient) : BaseAdapter(client) {
    override val protocol = ProtocolKind.OPENAI_RESPONSES

    internal fun body(r: ChatRequest): JsonObject = buildJsonObject {
        put("model", r.model)
        put("stream", true)
        if (r.systemPrompt.isNotBlank()) put("instructions", r.systemPrompt)
        put("temperature", r.temperature)
        put("top_p", r.topP)
        putJsonArray("input") {
            r.messages.forEach { message ->
                addJsonObject {
                    put("role", message.role)
                    if (message.attachments.none { it.mimeType.startsWith("image/") }) {
                        put("content", attachmentText(message))
                    } else putJsonArray("content") {
                        addJsonObject { put("type", "input_text"); put("text", attachmentText(message)) }
                        message.attachments.filter { it.mimeType.startsWith("image/") }.forEach { image ->
                            addJsonObject { put("type", "input_image"); put("image_url", imageDataUrl(image)) }
                        }
                    }
                }
            }
        }
        if (r.searchEnabled) putJsonArray("tools") { addJsonObject { put("type", "web_search") } }
    }

    override fun stream(request: ChatRequest, apiKey: String): Flow<ChatEvent> = flow {
        sse.execute(request(endpoint(request.baseUrl, "responses"), apiKey, body(request))).collect { (eventName, data) ->
            if (data == "[DONE]") return@collect
            val root = runCatching { wireJson.parseToJsonElement(data).jsonObject }.getOrElse {
                throw ProviderException(ProviderException.Category.MALFORMED, "Malformed OpenAI stream event.")
            }
            when (eventName ?: root["type"]?.jsonPrimitive?.contentOrNull) {
                "response.output_text.delta", "response.refusal.delta" -> root["delta"]?.jsonPrimitive?.contentOrNull?.let { emit(ChatEvent.TextDelta(it)) }
                "response.web_search_call.searching", "response.web_search_call.in_progress" -> emit(ChatEvent.SearchActivity("Searching the web…"))
                "response.completed" -> emit(ChatEvent.Completed)
                "response.failed" -> throw ProviderException(ProviderException.Category.SERVER, "OpenAI reported a failed response.")
            }
        }
    }.flowOn(Dispatchers.IO)
}

class OpenAiCompatibleAdapter(client: OkHttpClient) : BaseAdapter(client) {
    override val protocol = ProtocolKind.OPENAI_COMPATIBLE

    internal fun body(r: ChatRequest): JsonObject = buildJsonObject {
        put("model", r.model)
        put("stream", true)
        put("temperature", r.temperature)
        put("top_p", r.topP)
        putJsonArray("messages") {
            if (r.systemPrompt.isNotBlank()) addJsonObject { put("role", "system"); put("content", r.systemPrompt) }
            r.messages.forEach { message ->
                addJsonObject {
                    put("role", message.role)
                    if (message.attachments.none { it.mimeType.startsWith("image/") }) put("content", attachmentText(message))
                    else putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", attachmentText(message)) }
                        message.attachments.filter { it.mimeType.startsWith("image/") }.forEach { image ->
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", imageDataUrl(image)) }
                            }
                        }
                    }
                }
            }
        }
        if (r.searchEnabled) {
            if (r.baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                putJsonArray("plugins") { addJsonObject {
                    put("id", "web")
                    put("max_results", 5)
                } }
            } else {
                putJsonArray("tools") { addJsonObject { put("type", "web_search") } }
            }
        }
    }

    override fun stream(request: ChatRequest, apiKey: String): Flow<ChatEvent> = flow {
        sse.execute(request(endpoint(request.baseUrl, "chat/completions"), apiKey, body(request))).collect { (_, data) ->
            if (data == "[DONE]") { emit(ChatEvent.Completed); return@collect }
            val root = runCatching { wireJson.parseToJsonElement(data).jsonObject }.getOrElse {
                throw ProviderException(ProviderException.Category.MALFORMED, "Malformed compatible stream event.")
            }
            root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?.let { emit(ChatEvent.TextDelta(it)) }
        }
    }.flowOn(Dispatchers.IO)
}

class AnthropicAdapter(client: OkHttpClient) : BaseAdapter(client) {
    override val protocol = ProtocolKind.ANTHROPIC

    internal fun body(r: ChatRequest): JsonObject = buildJsonObject {
        put("model", r.model)
        put("max_tokens", 4096)
        put("stream", true)
        put("temperature", r.temperature)
        put("top_p", r.topP)
        if (r.systemPrompt.isNotBlank()) put("system", r.systemPrompt)
        putJsonArray("messages") {
            r.messages.forEach { message -> addJsonObject {
                put("role", message.role)
                putJsonArray("content") {
                    addJsonObject { put("type", "text"); put("text", attachmentText(message)) }
                    message.attachments.filter { it.mimeType.startsWith("image/") }.forEach { image ->
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", image.mimeType)
                                put("data", Base64.getEncoder().encodeToString(File(image.localPath).readBytes()))
                            }
                        }
                    }
                }
            } }
        }
        if (r.searchEnabled) putJsonArray("tools") { addJsonObject {
            put("type", "web_search_20250305"); put("name", "web_search"); put("max_uses", 5)
        } }
    }

    override fun stream(request: ChatRequest, apiKey: String): Flow<ChatEvent> = flow {
        val headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
        sse.execute(request(endpoint(request.baseUrl, "messages"), "", body(request), headers)).collect { (eventName, data) ->
            val root = runCatching { wireJson.parseToJsonElement(data).jsonObject }.getOrElse {
                throw ProviderException(ProviderException.Category.MALFORMED, "Malformed Anthropic stream event.")
            }
            when (eventName ?: root["type"]?.jsonPrimitive?.contentOrNull) {
                "content_block_delta" -> root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { emit(ChatEvent.TextDelta(it)) }
                "content_block_start" -> if (root["content_block"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull == "server_tool_use") emit(ChatEvent.SearchActivity("Searching the web…"))
                "message_stop" -> emit(ChatEvent.Completed)
                "error" -> throw ProviderException(ProviderException.Category.SERVER, "Anthropic reported a streaming error.")
            }
        }
    }.flowOn(Dispatchers.IO)
}

class GeminiAdapter(client: OkHttpClient) : BaseAdapter(client) {
    override val protocol = ProtocolKind.GEMINI

    internal fun body(r: ChatRequest): JsonObject = buildJsonObject {
        if (r.systemPrompt.isNotBlank()) putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", r.systemPrompt) } }
        }
        putJsonArray("contents") {
            r.messages.forEach { message -> addJsonObject {
                put("role", if (message.role == "assistant") "model" else "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", attachmentText(message)) }
                    message.attachments.filter { it.mimeType.startsWith("image/") }.forEach { image ->
                        addJsonObject { putJsonObject("inline_data") {
                            put("mime_type", image.mimeType)
                            put("data", Base64.getEncoder().encodeToString(File(image.localPath).readBytes()))
                        } }
                    }
                }
            } }
        }
        putJsonObject("generationConfig") { put("temperature", r.temperature); put("topP", r.topP) }
        if (r.searchEnabled) putJsonArray("tools") { addJsonObject { putJsonObject("google_search") {} } }
    }

    override fun stream(request: ChatRequest, apiKey: String): Flow<ChatEvent> = flow {
        val model = URLEncoder.encode(request.model, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val url = endpoint(request.baseUrl, "models/$model:streamGenerateContent") + "?alt=sse&key=" +
            URLEncoder.encode(apiKey, StandardCharsets.UTF_8.toString())
        sse.execute(request(url, "", body(request))).collect { (_, data) ->
            val root = runCatching { wireJson.parseToJsonElement(data).jsonObject }.getOrElse {
                throw ProviderException(ProviderException.Category.MALFORMED, "Malformed Gemini stream event.")
            }
            root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.forEach { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.let { emit(ChatEvent.TextDelta(it)) }
                }
            if (root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("groundingMetadata") != null) {
                emit(ChatEvent.SearchActivity("Google Search grounding used"))
            }
        }
        emit(ChatEvent.Completed)
    }.flowOn(Dispatchers.IO)
}

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .build()

class AdapterRegistry(client: OkHttpClient) {
    private val adapters = listOf(
        OpenAiResponsesAdapter(client),
        OpenAiCompatibleAdapter(client),
        AnthropicAdapter(client),
        GeminiAdapter(client)
    ).associateBy { it.protocol }

    operator fun get(protocol: ProtocolKind): ChatAdapter = requireNotNull(adapters[protocol])
}
