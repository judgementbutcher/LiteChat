package app.litechat.android.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class UpdateInstaller(private val context: Context, private val client: OkHttpClient) {
    suspend fun download(release: ReleaseInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates")
        check(directory.exists() || directory.mkdirs()) { "Unable to create the update directory." }
        val target = File(directory, "LiteChat-${release.tag.removePrefix("v")}-debug.apk")
        val temporary = File(directory, "${target.name}.download")
        temporary.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("APK download returned HTTP ${response.code}.")
                val body = response.body ?: throw IllegalStateException("APK download was empty.")
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (body.contentLength() > 0) onProgress((downloaded * 100 / body.contentLength()).toInt())
                        }
                    }
                }
            }
            val expected = checksum(release.checksumUrl)
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(actual.equals(expected, ignoreCase = true)) { "Downloaded APK failed its SHA-256 verification." }
            target.delete()
            check(temporary.renameTo(target)) { "Unable to save the verified update." }
            onProgress(100)
            target
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun launchInstallation(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun checksum(url: String): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Checksum download returned HTTP ${response.code}.")
            val value = SHA_256.find(response.body?.string().orEmpty())?.value
                ?: throw IllegalStateException("Release checksum is invalid.")
            return value
        }
    }

    private companion object {
        val SHA_256 = Regex("(?i)\\b[a-f0-9]{64}\\b")
    }
}
