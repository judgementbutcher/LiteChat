package app.litechat.android.network

import app.litechat.android.data.model.ProviderConfigEntity
import app.litechat.android.data.model.ProtocolKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ProviderApi(private val client: OkHttpClient) {
    suspend fun listModels(provider: ProviderConfigEntity, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        require(provider.baseUrl.startsWith("https://")) { "Only HTTPS provider URLs are allowed." }
        val base = provider.baseUrl.trimEnd('/')
        val url = if (provider.protocol == ProtocolKind.GEMINI) {
            "$base/models?key=${URLEncoder.encode(apiKey, StandardCharsets.UTF_8.toString())}"
        } else "$base/models"
        val request = Request.Builder().url(url).get().apply {
            when (provider.protocol) {
                ProtocolKind.ANTHROPIC -> { header("x-api-key", apiKey); header("anthropic-version", "2023-06-01") }
                ProtocolKind.GEMINI -> Unit
                else -> header("Authorization", "Bearer $apiKey")
            }
        }.build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderErrorMapper.fromHttp(response.code, text.take(4_096))
            val root = wireJson.parseToJsonElement(text).jsonObject
            val array = root[if (provider.protocol == ProtocolKind.GEMINI) "models" else "data"]?.jsonArray.orEmpty()
            array.mapNotNull { item ->
                val obj = item.jsonObject
                (obj["id"] ?: obj["name"])?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
            }.distinct().sorted()
        }
    }
}
