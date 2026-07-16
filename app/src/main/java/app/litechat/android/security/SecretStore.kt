package app.litechat.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AesGcmCodec {
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(key: SecretKey, payload: ByteArray): ByteArray {
        require(payload.size > 12) { "Invalid encrypted payload" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        return cipher.doFinal(payload.copyOfRange(12, payload.size))
    }
}

class SecretStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "provider-secrets.properties")
    private val mutex = Mutex()
    private val alias = "litechat-provider-key-v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    suspend fun put(providerId: String, apiKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val properties = readProperties()
            if (apiKey.isBlank()) properties.remove(providerId) else {
                val encrypted = AesGcmCodec.encrypt(key(), apiKey.toByteArray(Charsets.UTF_8))
                properties.setProperty(providerId, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { properties.store(it, null) }
        }
    }

    suspend fun get(providerId: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encoded = readProperties().getProperty(providerId) ?: return@withLock null
            runCatching {
                AesGcmCodec.decrypt(key(), Base64.decode(encoded, Base64.NO_WRAP)).toString(Charsets.UTF_8)
            }.getOrNull()
        }
    }

    suspend fun has(providerId: String): Boolean = get(providerId)?.isNotBlank() == true

    suspend fun remove(providerId: String) = put(providerId, "")

    private fun readProperties() = Properties().apply {
        if (file.exists()) file.inputStream().use(::load)
    }
}
