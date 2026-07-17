package app.litechat.android.data

import app.litechat.android.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupPayloadTest {
    @Test fun schemaTwoRoundTripContainsPinnedAtAndNoSecretField() {
        val pinnedAt = 42L
        val payload = BackupPayload(
            providers = listOf(ProviderConfigEntity("p", "Provider", ProtocolKind.OPENAI_COMPATIBLE, "https://example.com/v1")),
            models = listOf(ModelConfigEntity("p", "model")),
            conversations = listOf(ConversationEntity("c", "Chat", providerId = "p", modelId = "model", pinnedAt = pinnedAt)),
            messages = listOf(MessageEntity("m", "c", "assistant", "", "v")),
            variants = listOf(ResponseVariantEntity("v", "m", "answer", "p", "model", MessageStatus.COMPLETE)),
            attachments = emptyList(), templates = emptyList()
        )
        val json = Json.encodeToString(payload)
        assertFalse(json.contains("apiKey", ignoreCase = true))
        val restored = Json.decodeFromString<BackupPayload>(json)
        assertEquals(2, restored.schemaVersion)
        assertEquals(pinnedAt, restored.conversations.single().pinnedAt)
        assertEquals("answer", restored.variants.single().content)
    }

    @Test fun schemaOneWithoutPinnedAtStillImports() {
        val payload = BackupPayload(
            providers = emptyList(), models = emptyList(),
            conversations = listOf(ConversationEntity("c", "Legacy chat")),
            messages = emptyList(), variants = emptyList(), attachments = emptyList(), templates = emptyList()
        )
        val raw = Json { encodeDefaults = true }.encodeToString(payload)
            .replace("\"schemaVersion\":2", "\"schemaVersion\":1")
            .replace(Regex(",?\"pinnedAt\":null"), "")
        val restored = Json { ignoreUnknownKeys = true }.decodeFromString<BackupPayload>(raw)
        assertEquals(1, restored.schemaVersion)
        assertNull(restored.conversations.single().pinnedAt)
    }
}
