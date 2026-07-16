package app.litechat.android.security

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmCodecTest {
    @Test fun roundTripAndRandomIv() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plaintext = "unit-test-secret-is-never-logged".toByteArray()
        val first = AesGcmCodec.encrypt(key, plaintext)
        val second = AesGcmCodec.encrypt(key, plaintext)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plaintext, AesGcmCodec.decrypt(key, first))
    }

    @Test fun wrongKeyFails() {
        val generator = KeyGenerator.getInstance("AES").apply { init(128) }
        val encrypted = AesGcmCodec.encrypt(generator.generateKey(), "secret".toByteArray())
        assertThrows(Exception::class.java) { AesGcmCodec.decrypt(generator.generateKey(), encrypted) }
    }
}
