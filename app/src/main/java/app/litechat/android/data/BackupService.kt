package app.litechat.android.data

import android.content.Context
import android.util.Base64
import app.litechat.android.data.local.LiteChatDatabase
import app.litechat.android.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BackupAttachment(val entity: AttachmentEntity, val dataBase64: String? = null)

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val providers: List<ProviderConfigEntity>,
    val models: List<ModelConfigEntity>,
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val variants: List<ResponseVariantEntity>,
    val attachments: List<BackupAttachment>,
    val templates: List<PromptTemplateEntity>
)

class BackupService(private val context: Context, private val db: LiteChatDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val attachments = db.attachmentDao().getAll().map { entity ->
            val file = File(entity.localPath)
            BackupAttachment(entity.copy(localPath = ""), if (file.isFile && file.length() <= 10L * 1024 * 1024) Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) else null)
        }
        json.encodeToString(BackupPayload(
            providers = db.providerDao().getAll(), models = db.modelDao().getAll(), conversations = db.conversationDao().getAll(),
            messages = db.messageDao().getAll(), variants = db.variantDao().getAll(), attachments = attachments,
            templates = db.promptTemplateDao().getAll()
        ))
    }

    suspend fun importJson(raw: String) = withContext(Dispatchers.IO) {
        val payload = json.decodeFromString<BackupPayload>(raw)
        require(payload.schemaVersion == 1) { "Unsupported backup schema ${payload.schemaVersion}." }
        db.clearAllTables()
        db.providerDao().upsertAll(payload.providers)
        db.modelDao().upsertAll(payload.models)
        db.conversationDao().upsertAll(payload.conversations)
        db.messageDao().upsertAll(payload.messages)
        db.variantDao().upsertAll(payload.variants)
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val restored = payload.attachments.map { backed ->
            val bytes = backed.dataBase64?.let { Base64.decode(it, Base64.NO_WRAP) }
            val target = if (bytes != null) File(directory, "restored-${backed.entity.id}").apply { writeBytes(bytes) } else null
            backed.entity.copy(localPath = target?.absolutePath.orEmpty(), sizeBytes = target?.length() ?: 0)
        }
        if (restored.isNotEmpty()) db.attachmentDao().insertAll(restored)
        db.promptTemplateDao().upsertAll(payload.templates)
    }

    suspend fun exportMarkdown(conversationId: String): String = withContext(Dispatchers.IO) {
        val conversation = requireNotNull(db.conversationDao().get(conversationId))
        val messages = db.messageDao().getForConversation(conversationId)
        val variants = db.variantDao().getForConversation(conversationId).associateBy { it.id }
        buildString {
            appendLine("# ${conversation.title}")
            if (conversation.systemPrompt.isNotBlank()) appendLine("\n> System: ${conversation.systemPrompt.replace("\n", "\n> ")}")
            messages.forEach { message ->
                val content = if (message.role == "assistant") message.selectedVariantId?.let { variants[it]?.content }.orEmpty() else message.content
                appendLine("\n## ${if (message.role == "user") "User" else "Assistant"}\n")
                appendLine(content)
            }
        }
    }
}
