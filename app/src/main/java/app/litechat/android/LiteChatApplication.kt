package app.litechat.android

import android.app.Application
import app.litechat.android.attachment.AttachmentProcessor
import app.litechat.android.data.BackupService
import app.litechat.android.data.ConversationRepository
import app.litechat.android.data.local.LiteChatDatabase
import app.litechat.android.data.model.ModelConfigEntity
import app.litechat.android.data.model.ProtocolKind
import app.litechat.android.data.model.ProviderConfigEntity
import app.litechat.android.data.settings.UserSettingsStore
import app.litechat.android.network.*
import app.litechat.android.security.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiteChatApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val resources = application.resources
    val database = LiteChatDatabase.create(application)
    val settings = UserSettingsStore(application)
    val secrets = SecretStore(application)
    val httpClient = defaultHttpClient()
    val adapters = AdapterRegistry(httpClient)
    val providerApi = ProviderApi(httpClient)
    val repository = ConversationRepository(database, secrets, settings, adapters)
    val attachmentProcessor = AttachmentProcessor(application)
    val backupService = BackupService(application, database)
    val updateChecker = UpdateChecker(httpClient)
    val updateInstaller = UpdateInstaller(application, httpClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init { scope.launch { seedProviders() } }

    private suspend fun seedProviders() {
        // OpenRouter was part of an older built-in preset. Remove only that stable preset ID;
        // user-created providers (including new-api reverse proxies) use their own IDs and remain.
        database.providerDao().get("openrouter")?.let {
            database.providerDao().delete(it)
            runCatching { secrets.remove("openrouter") }
        }
        if (database.providerDao().count() != 0) return
        val providers = listOf(
            ProviderConfigEntity("openai", "OpenAI", ProtocolKind.OPENAI_RESPONSES, "https://api.openai.com/v1"),
            ProviderConfigEntity("anthropic", "Anthropic", ProtocolKind.ANTHROPIC, "https://api.anthropic.com/v1"),
            ProviderConfigEntity("gemini", "Gemini", ProtocolKind.GEMINI, "https://generativelanguage.googleapis.com/v1beta"),
            ProviderConfigEntity("deepseek", "DeepSeek", ProtocolKind.OPENAI_COMPATIBLE, "https://api.deepseek.com")
        )
        database.providerDao().upsertAll(providers)
        database.modelDao().upsertAll(listOf(
            ModelConfigEntity("openai", "gpt-4.1-mini", "GPT-4.1 mini", supportsVision = true, supportsSearch = true),
            ModelConfigEntity("anthropic", "claude-sonnet-4-20250514", "Claude Sonnet 4", supportsVision = true, supportsSearch = true),
            ModelConfigEntity("gemini", "gemini-2.5-flash", "Gemini 2.5 Flash", supportsVision = true, supportsSearch = true),
            ModelConfigEntity("deepseek", "deepseek-chat", "DeepSeek Chat")
        ))
    }
}
