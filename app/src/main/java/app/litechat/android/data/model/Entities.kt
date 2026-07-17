package app.litechat.android.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolKind { OPENAI_RESPONSES, OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }

@Serializable
enum class MessageStatus { COMPLETE, STREAMING, CANCELLED, ERROR }

@Serializable
@Entity(tableName = "providers")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: ProtocolKind,
    val baseUrl: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "models",
    primaryKeys = ["providerId", "modelId"],
    foreignKeys = [ForeignKey(
        entity = ProviderConfigEntity::class,
        parentColumns = ["id"],
        childColumns = ["providerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("providerId")]
)
data class ModelConfigEntity(
    val providerId: String,
    val modelId: String,
    val displayName: String = modelId,
    val supportsVision: Boolean = false,
    val supportsSearch: Boolean = false,
    val contextWindow: Int? = null,
    val enabled: Boolean = true
)

@Serializable
@Entity(
    tableName = "conversations",
    indices = [Index("updatedAt"), Index("providerId")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val systemPrompt: String = "",
    val providerId: String? = null,
    val modelId: String? = null,
    val searchEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val pinnedAt: Long? = null
)

@Serializable
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val selectedVariantId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "response_variants",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("messageId")]
)
data class ResponseVariantEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val content: String = "",
    val providerId: String,
    val modelId: String,
    val status: MessageStatus = MessageStatus.STREAMING,
    val errorMessage: String? = null,
    val searchSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("messageId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val extractedText: String? = null,
    val truncated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ConversationBundle(
    val conversation: ConversationEntity,
    val messages: List<MessageEntity>,
    val variants: List<ResponseVariantEntity>,
    val attachments: List<AttachmentEntity>
)
