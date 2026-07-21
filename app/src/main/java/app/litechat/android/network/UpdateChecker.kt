package app.litechat.android.network

import app.litechat.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReleaseInfo(
    val tag: String,
    val url: String,
    val apkUrl: String,
    val checksumUrl: String
)

class UpdateChecker(
    private val client: OkHttpClient,
    private val releasesUrl: String = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
) {
    val enabled = BuildConfig.GITHUB_OWNER.isNotBlank() && BuildConfig.GITHUB_REPO.isNotBlank()
    suspend fun check(): ReleaseInfo = withContext(Dispatchers.IO) {
        check(enabled) { "Update checking is not configured in this build." }
        val request = Request.Builder()
            .url(releasesUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("GitHub returned HTTP ${response.code}.")
            val root = wireJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val tag = root.getValue("tag_name").jsonPrimitive.content
            val version = tag.removePrefix("v")
            val assets = root["assets"]?.jsonArray.orEmpty()
            fun assetUrl(name: String): String = assets.firstOrNull { asset ->
                asset.jsonObject["name"]?.jsonPrimitive?.content == name
            }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
                ?: throw IllegalStateException("Release $tag is missing $name.")
            ReleaseInfo(
                tag = tag,
                url = root.getValue("html_url").jsonPrimitive.content,
                apkUrl = assetUrl("LiteChat-$version-debug.apk"),
                checksumUrl = assetUrl("LiteChat-$version-debug.apk.sha256")
            )
        }
    }
}

fun isNewerVersion(remote: String, local: String): Boolean {
    fun parts(value: String): List<Int>? {
        val raw = value.removePrefix("v").split(".")
        if (raw.size !in 1..3) return null
        val parsed = raw.map(String::toIntOrNull)
        if (parsed.any { it == null }) return null
        return List(3) { parsed.getOrNull(it) ?: 0 }.filterNotNull()
    }
    val remoteParts = parts(remote) ?: return false
    val localParts = parts(local) ?: return false
    return remoteParts.zip(localParts).firstOrNull { (candidate, installed) -> candidate != installed }
        ?.let { (candidate, installed) -> candidate > installed } ?: false
}
