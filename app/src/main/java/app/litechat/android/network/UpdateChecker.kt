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
    private val releasesUrl: String = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest",
    private val latestPageUrl: String = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
) {
    val enabled = BuildConfig.GITHUB_OWNER.isNotBlank() && BuildConfig.GITHUB_REPO.isNotBlank()
    suspend fun check(): ReleaseInfo = withContext(Dispatchers.IO) {
        check(enabled) { "Update checking is not configured in this build." }
        val request = Request.Builder()
            .url(releasesUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LiteChat/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403 || response.code == 429) return@withContext checkViaLatestPage(response.code)
                throw IllegalStateException("GitHub returned HTTP ${response.code}: ${response.errorSummary()}.")
            }
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

    private fun checkViaLatestPage(apiCode: Int): ReleaseInfo {
        val request = Request.Builder()
            .url(latestPageUrl)
            .header("User-Agent", "LiteChat/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "GitHub update check was rate-limited (HTTP $apiCode), and the fallback page returned HTTP ${response.code}."
                )
            }
            val tag = response.request.url.pathSegments.lastOrNull()
                ?.takeIf { it.startsWith("v") && it.length > 1 }
                ?: throw IllegalStateException("GitHub latest release did not include a version tag.")
            val version = tag.removePrefix("v")
            val base = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/download/$tag"
            return ReleaseInfo(
                tag = tag,
                url = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/tag/$tag",
                apkUrl = "$base/LiteChat-$version-debug.apk",
                checksumUrl = "$base/LiteChat-$version-debug.apk.sha256"
            )
        }
    }

    private fun okhttp3.Response.errorSummary(): String {
        val message = runCatching {
            wireJson.parseToJsonElement(body?.string().orEmpty()).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return message ?: "request failed"
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
