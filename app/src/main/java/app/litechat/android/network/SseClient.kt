package app.litechat.android.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
    fun execute(request: Request): Flow<Pair<String?, String>> = kotlinx.coroutines.flow.callbackFlow {
        val call: Call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!call.isCanceled()) {
                    close(ProviderException(ProviderException.Category.NETWORK, "Network error: ${error.message ?: "connection failed"}"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
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
                            val line = source.readUtf8Line() ?: break
                            parser.accept(line)?.let { frame ->
                                if (trySend(frame).isFailure) return
                            }
                        }
                        parser.finish()?.let { frame -> trySend(frame) }
                    }
                    close()
                } catch (error: Exception) {
                    if (!call.isCanceled()) {
                        close(
                            when (error) {
                                is ProviderException -> error
                                is CancellationException -> error
                                is IOException -> ProviderException(
                                    ProviderException.Category.NETWORK,
                                    "Network error: ${error.message ?: "connection failed"}"
                                )
                                else -> error
                            }
                        )
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }
}
