package app.litechat.android.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.litechat.android.AppContainer
import app.litechat.android.attachment.AttachmentProcessor
import app.litechat.android.data.model.*
import app.litechat.android.data.settings.ThemeMode
import app.litechat.android.network.ChatAttachment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {
    val conversations = container.repository.conversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val providers = container.repository.providers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val models = container.repository.models.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates = container.repository.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun searchConversations(query: String) = container.repository.searchConversations(query)
    fun archivedConversations(query: String) = container.repository.archivedConversations(query)
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.litechat.android.data.settings.UserSettings())

    private val selectedId = MutableStateFlow<String?>(null)
    val selectedConversation = selectedId.flatMapLatest { id ->
        id?.let(container.repository::conversation) ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages = selectedId.flatMapLatest { id -> id?.let(container.repository::messages) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val variants = selectedId.flatMapLatest { id -> id?.let(container.repository::variants) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val attachments = selectedId.flatMapLatest { id -> id?.let(container.repository::attachments) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val chatUiState = combine(selectedConversation, messages, variants, attachments) { conversation, messages, variants, attachments ->
        ChatUiState(
            conversation = conversation,
            messages = messages,
            variantsByMessage = variants.groupBy { it.messageId },
            attachmentsByMessage = attachments.groupBy { it.messageId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    val pendingAttachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val isGenerating = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)
    val providerStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val updateCheckEnabled: Boolean = container.updateChecker.enabled
    private var generationJob: Job? = null

    fun openConversation(id: String) { selectedId.value = id }

    fun createConversation(onCreated: (String) -> Unit) = viewModelScope.launch {
        runCatching { container.repository.createConversation() }
            .onSuccess(onCreated).onFailure { showError(it) }
    }

    fun send(content: String) {
        val id = selectedId.value ?: return
        if (isGenerating.value) return
        val pending = pendingAttachments.value
        pendingAttachments.value = emptyList()
        generationJob = viewModelScope.launch {
            isGenerating.value = true
            try { container.repository.send(id, content, pending) }
            catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) showError(e) }
            finally { isGenerating.value = false }
        }
    }

    fun stop() { generationJob?.cancel() }

    fun retry(messageId: String) {
        val id = selectedId.value ?: return
        if (isGenerating.value) return
        generationJob = viewModelScope.launch {
            isGenerating.value = true
            try { container.repository.retry(id, messageId) }
            catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) showError(e) }
            finally { isGenerating.value = false }
        }
    }

    fun editMessage(id: String, content: String) = viewModelScope.launch {
        runCatching { container.repository.editUserMessage(id, content) }.onFailure(::showError)
    }
    fun editAndRegenerate(id: String, content: String) {
        if (isGenerating.value) return
        generationJob = viewModelScope.launch {
            isGenerating.value = true
            try { container.repository.editAndRegenerate(id, content) }
            catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) showError(e) }
            finally { isGenerating.value = false }
        }
    }
    fun selectVariant(messageId: String, variantId: String) = viewModelScope.launch {
        runCatching { container.repository.selectVariant(messageId, variantId) }.onFailure(::showError)
    }
    fun updateConversation(value: ConversationEntity) = viewModelScope.launch {
        runCatching { container.repository.updateConversation(value) }.onFailure(::showError)
    }
    fun deleteConversation(value: ConversationEntity, after: () -> Unit) = viewModelScope.launch {
        runCatching { container.repository.deleteConversation(value) }.onSuccess { after() }.onFailure(::showError)
    }
    fun renameConversation(value: ConversationEntity, title: String) = viewModelScope.launch {
        runCatching { container.repository.renameConversation(value, title) }.onFailure(::showError)
    }
    fun setPinned(value: ConversationEntity, pinned: Boolean) = viewModelScope.launch {
        runCatching { container.repository.setPinned(value, pinned) }.onFailure(::showError)
    }
    fun setArchived(value: ConversationEntity, archived: Boolean, after: () -> Unit = {}) = viewModelScope.launch {
        runCatching { container.repository.setArchived(value, archived) }.onSuccess { after() }.onFailure(::showError)
    }

    fun addAttachments(uris: List<Uri>) = viewModelScope.launch {
        val remaining = AttachmentProcessor.MAX_FILES - pendingAttachments.value.size
        if (remaining <= 0) { notice.value = text(app.litechat.android.R.string.attachment_limit); return@launch }
        uris.take(remaining).forEach { uri ->
            runCatching { container.attachmentProcessor.process(uri) }
                .onSuccess { pendingAttachments.value += it }
                .onFailure(::showError)
        }
        if (uris.size > remaining) notice.value = text(app.litechat.android.R.string.attachment_first_four)
    }
    fun removePending(index: Int) { pendingAttachments.value = pendingAttachments.value.filterIndexed { i, _ -> i != index } }

    fun saveProvider(provider: ProviderConfigEntity, key: String?) = viewModelScope.launch {
        runCatching { container.repository.saveProvider(provider, key) }
            .onSuccess { notice.value = text(app.litechat.android.R.string.provider_saved) }.onFailure(::showError)
    }
    fun deleteProvider(provider: ProviderConfigEntity) = viewModelScope.launch {
        runCatching { container.repository.deleteProvider(provider) }.onFailure(::showError)
    }
    fun testProvider(provider: ProviderConfigEntity) = viewModelScope.launch {
        providerStatus.value += provider.id to text(app.litechat.android.R.string.testing)
        runCatching {
            val key = container.repository.apiKey(provider.id)
            if (key.isBlank()) error(text(app.litechat.android.R.string.save_key_first))
            container.providerApi.listModels(provider, key)
        }.onSuccess { list ->
            list.forEach { modelId -> if (models.value.none { it.providerId == provider.id && it.modelId == modelId }) container.repository.saveModel(ModelConfigEntity(provider.id, modelId, modelId)) }
            providerStatus.value += provider.id to text(app.litechat.android.R.string.connected_models, list.size)
        }
            .onFailure { providerStatus.value += provider.id to (it.message ?: text(app.litechat.android.R.string.connection_failed)) }
    }
    fun saveModel(model: ModelConfigEntity) = viewModelScope.launch {
        runCatching { container.repository.saveModel(model) }.onFailure(::showError)
    }

    fun saveTemplate(id: String?, title: String, content: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        runCatching { container.repository.saveTemplate(PromptTemplateEntity(id ?: UUID.randomUUID().toString(), title, content, updatedAt = now)) }
            .onFailure(::showError)
    }
    fun deleteTemplate(value: PromptTemplateEntity) = viewModelScope.launch { container.repository.deleteTemplate(value) }

    fun exportBackup(deliver: (String) -> Unit) = viewModelScope.launch {
        runCatching { container.backupService.exportJson() }.onSuccess(deliver).onFailure(::showError)
    }
    fun importBackup(raw: String) = viewModelScope.launch {
        runCatching { container.backupService.importJson(raw) }
            .onSuccess { notice.value = text(app.litechat.android.R.string.backup_restored) }.onFailure(::showError)
    }
    fun exportMarkdown(deliver: (String) -> Unit) = viewModelScope.launch {
        val id = selectedId.value ?: return@launch
        runCatching { container.backupService.exportMarkdown(id) }.onSuccess(deliver).onFailure(::showError)
    }

    fun setTheme(value: ThemeMode) = viewModelScope.launch { container.settings.setTheme(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { container.settings.setDynamicColor(value) }
    fun setLanguage(value: String) = viewModelScope.launch { container.settings.setLanguage(value) }
    fun setParameters(temperature: Float, topP: Float) = viewModelScope.launch { container.settings.setParameters(temperature, topP) }
    fun checkForUpdate() = viewModelScope.launch {
        runCatching { container.updateChecker.check() }
            .onSuccess { notice.value = text(app.litechat.android.R.string.latest_release, it.tag, it.url) }.onFailure(::showError)
    }
    fun clearNotice() { notice.value = null }

    private fun text(id: Int, vararg args: Any): String = container.resources.getString(id, *args)
    private fun showError(error: Throwable) { notice.value = error.message ?: text(app.litechat.android.R.string.unknown_error) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
