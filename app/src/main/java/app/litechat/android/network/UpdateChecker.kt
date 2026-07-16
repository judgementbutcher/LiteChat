package app.litechat.android.network

import app.litechat.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReleaseInfo(val tag: String, val url: String)

class UpdateChecker(private val client: OkHttpClient) {
    val enabled = BuildConfig.GITHUB_OWNER.isNotBlank() && BuildConfig.GITHUB_REPO.isNotBlank()
    suspend fun check(): ReleaseInfo = withContext(Dispatchers.IO) {
        check(enabled) { "Update checking is not configured in this build." }
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("GitHub returned HTTP ${response.code}.")
            val root = wireJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            ReleaseInfo(root.getValue("tag_name").jsonPrimitive.content, root.getValue("html_url").jsonPrimitive.content)
        }
    }
}
