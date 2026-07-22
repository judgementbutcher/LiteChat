package app.litechat.android.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.litechat.android.AppContainer
import app.litechat.android.BuildConfig
import app.litechat.android.attachment.AttachmentProcessor
import app.litechat.android.data.model.*
import app.litechat.android.data.settings.ThemeMode
import app.litechat.android.network.ChatAttachment
import app.litechat.android.network.isNewerVersion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {
    val conversations = container.repository.conversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val providers = container.repository.providers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val models = container.repository.models.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates = container.repository.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun searchConversations(query: Flow<String>) = query
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { container.repository.searchConversations(it) }
    fun archivedConversations(query: Flow<String>) = query
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { container.repository.archivedConversations(it) }
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
    private val variantsByMessage = variants
        .scan(emptyMap<String, List<ResponseVariantEntity>>()) { previous, values ->
            stableVariantGroups(values, previous)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    private val attachmentsByMessage = attachments
        .map { values -> values.groupBy { it.messageId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val chatUiState = combine(selectedConversation, messages, variantsByMessage, attachmentsByMessage) { conversation, messages, variantGroups, attachmentGroups ->
        ChatUiState(
            conversation = conversation,
            messages = messages,
            variantsByMessage = variantGroups,
            attachmentsByMessage = attachmentGroups
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    val pendingAttachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val isGenerating = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)
    val providerStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val updateCheckEnabled: Boolean = container.updateChecker.enabled
    private var generationJob: Job? = null
    private var updateJob: Job? = null
    val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)

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

    fun retryWith(messageId: String, model: ModelConfigEntity) {
        val id = selectedId.value ?: return
        if (isGenerating.value) return
        generationJob = viewModelScope.launch {
            isGenerating.value = true
            try { container.repository.retryWith(id, messageId, model.providerId, model.modelId) }
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
            val existingIds = models.value.asSequence()
                .filter { it.providerId == provider.id }
                .mapTo(hashSetOf()) { it.modelId }
            val additions = list.distinct()
                .filterNot { it in existingIds }
                .map { modelId -> ModelConfigEntity(provider.id, modelId, modelId) }
            container.repository.saveModels(additions)
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
    fun checkForUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            updateState.value = UpdateUiState.Checking
            runCatching { container.updateChecker.check() }
                .onSuccess { release ->
                    updateState.value = if (isNewerVersion(release.tag, BuildConfig.VERSION_NAME)) {
                        UpdateUiState.Available(release)
                    } else {
                        UpdateUiState.UpToDate
                    }
                }
                .onFailure { error -> updateState.value = UpdateUiState.Failed(error.message ?: text(app.litechat.android.R.string.unknown_error)) }
        }
    }

    fun downloadUpdate() {
        val release = (updateState.value as? UpdateUiState.Available)?.release ?: return
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            updateState.value = UpdateUiState.Downloading(release, 0)
            runCatching {
                container.updateInstaller.download(release) { progress ->
                    updateState.value = UpdateUiState.Downloading(release, progress.coerceIn(0, 100))
                }
            }.onSuccess { file ->
                updateState.value = UpdateUiState.Ready(release, file)
            }.onFailure { error ->
                updateState.value = UpdateUiState.Failed(error.message ?: text(app.litechat.android.R.string.unknown_error))
            }
        }
    }

    fun installUpdate() {
        val ready = updateState.value as? UpdateUiState.Ready ?: return
        runCatching { container.updateInstaller.launchInstallation(ready.file) }
            .onSuccess { started ->
                if (!started) updateState.value = ready.copy(permissionRequired = true)
            }
            .onFailure { error -> updateState.value = UpdateUiState.Failed(error.message ?: text(app.litechat.android.R.string.unknown_error)) }
    }
    fun clearNotice() { notice.value = null }

    private fun text(id: Int, vararg args: Any): String = container.resources.getString(id, *args)
    private fun showError(error: Throwable) { notice.value = error.message ?: text(app.litechat.android.R.string.unknown_error) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }

    companion object {
        /** Quiet period before a typed conversation search hits the database; empty query is immediate. */
        private const val SEARCH_DEBOUNCE_MS = 220L
    }
}

internal fun stableVariantGroups(
    values: List<ResponseVariantEntity>,
    previous: Map<String, List<ResponseVariantEntity>>
): Map<String, List<ResponseVariantEntity>> = values.groupBy { it.messageId }.mapValues { (messageId, current) ->
    previous[messageId]?.takeIf { old ->
        old.size == current.size && old.indices.all { index ->
            val prior = old[index]
            val next = current[index]
            prior.id == next.id &&
                prior.updatedAt == next.updatedAt &&
                prior.content.length == next.content.length &&
                prior.status == next.status &&
                prior.errorMessage == next.errorMessage &&
                prior.searchSummary == next.searchSummary
        }
    } ?: current
}
