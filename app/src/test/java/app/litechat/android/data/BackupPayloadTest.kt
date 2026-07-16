package app.litechat.android.data

import app.litechat.android.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupPayloadTest {
    @Test fun schemaOneRoundTripContainsVersionsAndNoSecretField() {
        val payload = BackupPayload(
            providers = listOf(ProviderConfigEntity("p", "Provider", ProtocolKind.OPENAI_COMPATIBLE, "https://example.com/v1")),
            models = listOf(ModelConfigEntity("p", "model")),
            conversations = listOf(ConversationEntity("c", "Chat", providerId = "p", modelId = "model")),
            messages = listOf(MessageEntity("m", "c", "assistant", "", "v")),
            variants = listOf(ResponseVariantEntity("v", "m", "answer", "p", "model", MessageStatus.COMPLETE)),
            attachments = emptyList(), templates = emptyList()
        )
        val json = Json.encodeToString(payload)
        assertFalse(json.contains("apiKey", ignoreCase = true))
        val restored = Json.decodeFromString<BackupPayload>(json)
        assertEquals(1, restored.schemaVersion)
        assertEquals("answer", restored.variants.single().content)
    }
}
