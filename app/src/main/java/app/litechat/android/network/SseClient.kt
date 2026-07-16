package app.litechat.android.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class SseFrameParser {
    private val data = mutableListOf<String>()
    private var event: String? = null

    fun accept(line: String): Pair<String?, String>? {
        if (line.isEmpty()) {
            if (data.isEmpty()) return null
            return (event to data.joinToString("\n")).also {
                event = null
                data.clear()
            }
        }
        when {
            line.startsWith("event:") -> event = line.substringAfter(':').trim()
            line.startsWith("data:") -> data += line.substringAfter(':').trimStart()
            line.startsWith(":") -> Unit
        }
        return null
    }

    fun finish(): Pair<String?, String>? = if (data.isEmpty()) null else (event to data.joinToString("\n")).also {
        event = null
        data.clear()
    }
}

class SseClient(private val client: OkHttpClient) {
    fun execute(request: Request): Flow<Pair<String?, String>> = flow {
        val call: Call = client.newCall(request)
        try {
            val response = call.execute()
            response.use {
                if (!it.isSuccessful) {
                    val body = it.body?.string().orEmpty().take(4_096)
                    throw ProviderErrorMapper.fromHttp(it.code, body)
                }
                val source = it.body?.source() ?: throw ProviderException(
                    ProviderException.Category.MALFORMED,
                    "Provider returned an empty response."
                )
                val parser = SseFrameParser()
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    parser.accept(line)?.let { frame -> emit(frame) }
                }
                parser.finish()?.let { frame -> emit(frame) }
            }
        } catch (e: ProviderException) {
            throw e
        } catch (e: IOException) {
            throw ProviderException(ProviderException.Category.NETWORK, "Network error: ${e.message ?: "connection failed"}")
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
