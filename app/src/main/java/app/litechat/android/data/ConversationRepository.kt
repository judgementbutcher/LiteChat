package app.litechat.android.data

import android.os.SystemClock
import app.litechat.android.data.local.LiteChatDatabase
import app.litechat.android.data.model.*
import app.litechat.android.data.settings.UserSettingsStore
import app.litechat.android.network.*
import app.litechat.android.security.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ConversationRepository(
    private val db: LiteChatDatabase,
    private val secrets: SecretStore,
    private val settingsStore: UserSettingsStore,
    private val adapters: AdapterRegistry
) {
    val conversations = db.conversationDao().observeAll()
    val providers = db.providerDao().observeAll()
    val models = db.modelDao().observeAll()
    val templates = db.promptTemplateDao().observeAll()
    fun searchConversations(query: String) = db.conversationDao().observeSearch(query.trim())

    fun conversation(id: String) = db.conversationDao().observe(id)
    fun messages(id: String) = db.messageDao().observeForConversation(id)
    fun variants(id: String) = db.variantDao().observeForConversation(id)
    fun attachments(id: String) = db.attachmentDao().observeForConversation(id)

    suspend fun createConversation(providerId: String? = null, modelId: String? = null): String {
        val enabledModels = db.modelDao().getAll().filter { it.enabled }
        val picked = enabledModels.firstOrNull { providerId == null || it.providerId == providerId }
        val id = UUID.randomUUID().toString()
        db.conversationDao().upsert(ConversationEntity(
            id = id,
            title = "New chat",
            providerId = providerId ?: picked?.providerId,
            modelId = modelId ?: picked?.modelId
        ))
        return id
    }

    suspend fun updateConversation(value: ConversationEntity) = db.conversationDao().upsert(value.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteConversation(value: ConversationEntity) = db.conversationDao().delete(value)

    suspend fun send(conversationId: String, content: String, pending: List<ChatAttachment>) {
        require(content.isNotBlank() || pending.isNotEmpty())
        require(pending.size <= 4)
        val now = System.currentTimeMillis()
        val userId = UUID.randomUUID().toString()
        val user = MessageEntity(userId, conversationId, "user", content.trim(), createdAt = now, updatedAt = now)
        db.messageDao().upsert(user)
        if (pending.isNotEmpty()) db.attachmentDao().insertAll(pending.map { item ->
            AttachmentEntity(
                id = UUID.randomUUID().toString(), messageId = userId, displayName = item.displayName,
                mimeType = item.mimeType, localPath = item.localPath, sizeBytes = runCatching { java.io.File(item.localPath).length() }.getOrDefault(0),
                extractedText = item.extractedText, truncated = item.truncated
            )
        })
        val conversation = requireNotNull(db.conversationDao().get(conversationId))
        val title = if (conversation.title == "New chat") content.trim().lineSequence().firstOrNull().orEmpty().ifBlank { pending.first().displayName }.take(48) else conversation.title
        db.conversationDao().upsert(conversation.copy(title = title, updatedAt = now))
        val assistantId = UUID.randomUUID().toString()
        db.messageDao().upsert(MessageEntity(assistantId, conversationId, "assistant", "", createdAt = now + 1, updatedAt = now + 1))
        generate(conversationId, assistantId)
    }

    suspend fun retry(conversationId: String, assistantMessageId: String) = generate(conversationId, assistantMessageId)

    private suspend fun generate(conversationId: String, assistantMessageId: String) {
        val conversation = requireNotNull(db.conversationDao().get(conversationId))
        val providerId = conversation.providerId ?: throw ProviderException(ProviderException.Category.UNSUPPORTED, "Choose a provider and model first.")
        val modelId = conversation.modelId ?: throw ProviderException(ProviderException.Category.UNSUPPORTED, "Choose a model first.")
        val provider = db.providerDao().get(providerId) ?: throw ProviderException(ProviderException.Category.UNSUPPORTED, "The selected provider no longer exists.")
        val model = db.modelDao().get(providerId, modelId)
        if (conversation.searchEnabled && model?.supportsSearch != true) throw ProviderException(ProviderException.Category.UNSUPPORTED, "This model is not marked as supporting native search.")
        val apiKey = secrets.get(providerId).orEmpty()
        if (apiKey.isBlank()) throw ProviderException(ProviderException.Category.AUTHENTICATION, "Add an API key for ${provider.name}.")
        val messages = db.messageDao().getForConversation(conversationId)
        val cutoff = messages.first { it.id == assistantMessageId }.createdAt
        val included = messages.filter { it.createdAt < cutoff }
        val variants = db.variantDao().getForConversation(conversationId).associateBy { it.id }
        val attachments = db.attachmentDao().getForConversation(conversationId).groupBy { it.messageId }
        if (included.flatMap { attachments[it.id].orEmpty() }.any { it.mimeType.startsWith("image/") } && model?.supportsVision != true) {
            throw ProviderException(ProviderException.Category.UNSUPPORTED, "This model is not marked as supporting image input.")
        }
        val inputs = included.mapNotNull { message ->
            val text = if (message.role == "assistant") message.selectedVariantId?.let { variants[it]?.content } ?: return@mapNotNull null else message.content
            ChatInputMessage(message.role, text, attachments[message.id].orEmpty().map { it.toChatAttachment() })
        }
        val setting = settingsStore.settings.first()
        val variantId = UUID.randomUUID().toString()
        val base = ResponseVariantEntity(variantId, assistantMessageId, providerId = providerId, modelId = modelId)
        db.variantDao().upsert(base)
        val assistant = requireNotNull(db.messageDao().get(assistantMessageId))
        db.messageDao().update(assistant.copy(selectedVariantId = variantId, updatedAt = System.currentTimeMillis()))
        val request = ChatRequest(provider.baseUrl, modelId, conversation.systemPrompt, inputs, setting.temperature, setting.topP, conversation.searchEnabled)
        val output = StringBuilder()
        var searchSummary: String? = null
        var lastWrite = 0L
        try {
            adapters[provider.protocol].stream(request, apiKey).collect { event ->
                when (event) {
                    is ChatEvent.TextDelta -> {
                        output.append(event.text)
                        val elapsed = SystemClock.elapsedRealtime()
                        if (elapsed - lastWrite >= 120) {
                            db.variantDao().upsert(base.copy(content = output.toString(), updatedAt = System.currentTimeMillis()))
                            lastWrite = elapsed
                        }
                    }
                    is ChatEvent.SearchActivity -> {
                        searchSummary = event.summary
                        db.variantDao().upsert(base.copy(content = output.toString(), searchSummary = searchSummary, updatedAt = System.currentTimeMillis()))
                    }
                    ChatEvent.Completed -> Unit
                }
            }
            db.variantDao().upsert(base.copy(content = output.toString(), status = MessageStatus.COMPLETE, searchSummary = searchSummary, updatedAt = System.currentTimeMillis()))
        } catch (cancelled: CancellationException) {
            db.variantDao().upsert(base.copy(content = output.toString(), status = MessageStatus.CANCELLED, errorMessage = "Generation stopped", searchSummary = searchSummary, updatedAt = System.currentTimeMillis()))
            throw cancelled
        } catch (error: Exception) {
            db.variantDao().upsert(base.copy(content = output.toString(), status = MessageStatus.ERROR, errorMessage = error.message ?: "Generation failed", searchSummary = searchSummary, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun selectVariant(messageId: String, variantId: String) {
        val message = requireNotNull(db.messageDao().get(messageId))
        db.messageDao().update(message.copy(selectedVariantId = variantId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun editUserMessage(messageId: String, content: String) {
        val message = requireNotNull(db.messageDao().get(messageId))
        require(message.role == "user")
        db.messageDao().update(message.copy(content = content.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveProvider(provider: ProviderConfigEntity, apiKey: String?) {
        require(provider.baseUrl.startsWith("https://") && provider.baseUrl.toHttpUrlOrNull() != null) { "A valid HTTPS URL is required." }
        db.providerDao().upsert(provider.copy(baseUrl = provider.baseUrl.trimEnd('/'), updatedAt = System.currentTimeMillis()))
        if (apiKey != null) secrets.put(provider.id, apiKey.trim())
    }

    suspend fun deleteProvider(provider: ProviderConfigEntity) {
        db.providerDao().delete(provider)
        secrets.remove(provider.id)
    }
    suspend fun hasKey(providerId: String) = secrets.has(providerId)
    suspend fun apiKey(providerId: String) = secrets.get(providerId).orEmpty()
    suspend fun saveModel(model: ModelConfigEntity) = db.modelDao().upsert(model)
    suspend fun saveTemplate(value: PromptTemplateEntity) = db.promptTemplateDao().upsert(value)
    suspend fun deleteTemplate(value: PromptTemplateEntity) = db.promptTemplateDao().delete(value)

    private fun AttachmentEntity.toChatAttachment() = ChatAttachment(displayName, mimeType, localPath, extractedText, truncated)
}
