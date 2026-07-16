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
    fun selectVariant(messageId: String, variantId: String) = viewModelScope.launch {
        runCatching { container.repository.selectVariant(messageId, variantId) }.onFailure(::showError)
    }
    fun updateConversation(value: ConversationEntity) = viewModelScope.launch {
        runCatching { container.repository.updateConversation(value) }.onFailure(::showError)
    }
    fun deleteConversation(value: ConversationEntity, after: () -> Unit) = viewModelScope.launch {
        runCatching { container.repository.deleteConversation(value) }.onSuccess { after() }.onFailure(::showError)
    }

    fun addAttachments(uris: List<Uri>) = viewModelScope.launch {
        val remaining = AttachmentProcessor.MAX_FILES - pendingAttachments.value.size
        if (remaining <= 0) { notice.value = "A message can have at most 4 attachments."; return@launch }
        uris.take(remaining).forEach { uri ->
            runCatching { container.attachmentProcessor.process(uri) }
                .onSuccess { pendingAttachments.value += it }
                .onFailure(::showError)
        }
        if (uris.size > remaining) notice.value = "Only the first 4 attachments were added."
    }
    fun removePending(index: Int) { pendingAttachments.value = pendingAttachments.value.filterIndexed { i, _ -> i != index } }

    fun saveProvider(provider: ProviderConfigEntity, key: String?) = viewModelScope.launch {
        runCatching { container.repository.saveProvider(provider, key) }
            .onSuccess { notice.value = "Provider saved." }.onFailure(::showError)
    }
    fun deleteProvider(provider: ProviderConfigEntity) = viewModelScope.launch {
        runCatching { container.repository.deleteProvider(provider) }.onFailure(::showError)
    }
    fun testProvider(provider: ProviderConfigEntity) = viewModelScope.launch {
        providerStatus.value += provider.id to "Testing…"
        runCatching {
            val key = container.repository.apiKey(provider.id)
            if (key.isBlank()) error("Save an API key first.")
            container.providerApi.listModels(provider, key)
        }.onSuccess { list ->
            list.forEach { modelId -> if (models.value.none { it.providerId == provider.id && it.modelId == modelId }) container.repository.saveModel(ModelConfigEntity(provider.id, modelId, modelId)) }
            providerStatus.value += provider.id to "Connected · imported ${list.size} models"
        }
            .onFailure { providerStatus.value += provider.id to (it.message ?: "Connection failed") }
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
            .onSuccess { notice.value = "Backup restored. API keys were not changed." }.onFailure(::showError)
    }
    fun exportMarkdown(deliver: (String) -> Unit) = viewModelScope.launch {
        val id = selectedId.value ?: return@launch
        runCatching { container.backupService.exportMarkdown(id) }.onSuccess(deliver).onFailure(::showError)
    }

    fun setTheme(value: ThemeMode) = viewModelScope.launch { container.settings.setTheme(value) }
    fun setLanguage(value: String) = viewModelScope.launch { container.settings.setLanguage(value) }
    fun setParameters(temperature: Float, topP: Float) = viewModelScope.launch { container.settings.setParameters(temperature, topP) }
    fun checkForUpdate() = viewModelScope.launch {
        runCatching { container.updateChecker.check() }
            .onSuccess { notice.value = "Latest release: ${it.tag} · ${it.url}" }.onFailure(::showError)
    }
    fun clearNotice() { notice.value = null }

    private fun showError(error: Throwable) { notice.value = error.message ?: "Something went wrong." }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
