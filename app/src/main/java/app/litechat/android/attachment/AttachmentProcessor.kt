package app.litechat.android.attachment

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import app.litechat.android.network.ChatAttachment
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import kotlin.math.roundToInt

class AttachmentException(message: String) : Exception(message)

class AttachmentProcessor(private val context: Context) {
    companion object {
        const val MAX_FILES = 4
        const val MAX_BYTES = 10L * 1024 * 1024
        const val MAX_CHARS = 100_000
        const val MAX_IMAGE_EDGE = 2048

        fun truncateText(value: String): Pair<String, Boolean> =
            if (value.length > MAX_CHARS) value.take(MAX_CHARS) to true else value to false
    }

    suspend fun process(uri: Uri): ChatAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (name, declaredSize) = metadata(resolver, uri)
        if (declaredSize != null && declaredSize > MAX_BYTES) throw AttachmentException("$name is larger than 10 MiB.")
        val mime = resolver.getType(uri) ?: mimeFromName(name)
        if (mime != "application/pdf" && mime != "text/plain" && mime != "text/markdown" && !mime.startsWith("image/")) {
            throw AttachmentException("Unsupported attachment type: $mime")
        }
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val raw = File(directory, "${UUID.randomUUID()}-${safeName(name)}")
        try {
            resolver.openInputStream(uri)?.use { input -> raw.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) throw AttachmentException("$name is larger than 10 MiB.")
                    output.write(buffer, 0, count)
                }
            } } ?: throw AttachmentException("Unable to read $name.")
        } catch (error: Exception) {
            raw.delete()
            throw error
        }
        if (raw.length() > MAX_BYTES) { raw.delete(); throw AttachmentException("$name is larger than 10 MiB.") }

        when {
            mime.startsWith("image/") -> processImage(raw, name, mime)
            mime == "application/pdf" -> {
                val extracted = runCatching { PDDocument.load(raw).use { PDFTextStripper().getText(it) } }
                    .getOrElse { throw AttachmentException("Unable to extract text from $name.") }
                if (extracted.isBlank()) { raw.delete(); throw AttachmentException("$name contains no extractable text. Scanned PDF OCR is not supported.") }
                val (text, truncated) = truncateText(extracted)
                ChatAttachment(name, mime, raw.absolutePath, text, truncated)
            }
            else -> {
                val bytes = raw.readBytes()
                val decoded = decodeText(bytes)
                val (text, truncated) = truncateText(decoded)
                ChatAttachment(name, mime, raw.absolutePath, text, truncated)
            }
        }
    }

    private fun processImage(raw: File, name: String, mime: String): ChatAttachment {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(raw.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AttachmentException("$name is not a readable image.")
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_EDGE * 2 || bounds.outHeight / sample > MAX_IMAGE_EDGE * 2) sample *= 2
        val bitmap = BitmapFactory.decodeFile(raw.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw AttachmentException("$name is not a readable image.")
        val scale = minOf(1f, MAX_IMAGE_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(),
            true
        ) else bitmap
        val usePng = mime == "image/png"
        val target = File(raw.parentFile, "${raw.nameWithoutExtension}.${if (usePng) "png" else "jpg"}")
        target.outputStream().use { resized.compress(if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, it) }
        if (target != raw) raw.delete()
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        if (target.length() > MAX_BYTES) { target.delete(); throw AttachmentException("$name remains larger than 10 MiB after resizing.") }
        return ChatAttachment(name, if (usePng) "image/png" else "image/jpeg", target.absolutePath)
    }

    private fun metadata(resolver: ContentResolver, uri: Uri): Pair<String, Long?> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0) ?: "attachment"
                val size = if (cursor.isNull(1)) null else cursor.getLong(1)
                return name to size
            }
        }
        return (uri.lastPathSegment ?: "attachment") to null
    }

    private fun decodeText(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> bytes.copyOfRange(2, bytes.size).toString(Charset.forName("UTF-16BE"))
        else -> bytes.toString(Charsets.UTF_8)
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "txt" -> "text/plain"; "md", "markdown" -> "text/markdown"; "pdf" -> "application/pdf"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"
        else -> "application/octet-stream"
    }

    private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "attachment" }
}
