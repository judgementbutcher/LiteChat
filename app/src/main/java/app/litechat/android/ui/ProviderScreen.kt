package app.litechat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litechat.android.R
import app.litechat.android.data.model.*
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import java.util.UUID

@Composable
fun ProviderScreen(viewModel: AppViewModel, openDrawer: (() -> Unit)?) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val statuses by viewModel.providerStatus.collectAsStateWithLifecycle()
    var editingProvider by remember { mutableStateOf<ProviderConfigEntity?>(null) }
    var addProvider by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<ModelConfigEntity?>(null) }
    ScreenScaffold(
        stringResource(R.string.providers),
        openDrawer,
        floatingActionButton = {
            FloatingActionButton({ addProvider = true }) { Icon(Lucide.Plus, stringResource(R.string.add_provider)) }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.no_key_hint), style = MaterialTheme.typography.bodyMedium)
            }
            items(providers, key = { it.id }) { provider ->
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(provider.name, style = MaterialTheme.typography.titleMedium)
                                Text(provider.protocol.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                                Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { editingProvider = provider }) { Text(stringResource(R.string.edit)) }
                        }
                        statuses[provider.id]?.let { Text(it, color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row {
                            OutlinedButton(onClick = { viewModel.testProvider(provider) }) { Text(stringResource(R.string.test_connection)) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { editingModel = ModelConfigEntity(provider.id, "", "") }) { Text(stringResource(R.string.add_model)) }
                        }
                        models.filter { it.providerId == provider.id }.forEach { model ->
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName.ifBlank { model.modelId })
                                    Text(buildString {
                                        append(model.modelId)
                                        if (model.supportsVision) append(" · vision")
                                        if (model.supportsSearch) append(" · search")
                                    }, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { editingModel = model }) { Text(stringResource(R.string.edit)) }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (addProvider) ProviderDialog(null, { addProvider = false }, { provider, key -> viewModel.saveProvider(provider, key); addProvider = false }, null)
    editingProvider?.let { provider -> ProviderDialog(provider, { editingProvider = null }, { value, key -> viewModel.saveProvider(value, key); editingProvider = null }, { viewModel.deleteProvider(provider); editingProvider = null }) }
    editingModel?.let { model -> ModelDialog(model, { editingModel = null }) { viewModel.saveModel(it); editingModel = null } }
}

@Composable
private fun ProviderDialog(
    existing: ProviderConfigEntity?,
    dismiss: () -> Unit,
    save: (ProviderConfigEntity, String?) -> Unit,
    delete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "https://") }
    var protocol by remember { mutableStateOf(existing?.protocol ?: ProtocolKind.OPENAI_COMPATIBLE) }
    var key by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(existing?.name ?: stringResource(R.string.add_provider)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.provider)) }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) { Text(protocol.name.replace('_', ' '), Modifier.fillMaxWidth()) }
                    DropdownMenu(menu, { menu = false }) {
                        ProtocolKind.entries.forEach { value -> DropdownMenuItem(text = { Text(value.name.replace('_', ' ')) }, onClick = { protocol = value; menu = false }) }
                    }
                }
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text(stringResource(R.string.base_url)) }, singleLine = true)
                OutlinedTextField(
                    key, { key = it }, label = { Text(if (existing == null) stringResource(R.string.api_key) else "${stringResource(R.string.api_key)} · leave blank to keep") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                if (delete != null) TextButton(delete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val now = System.currentTimeMillis()
            save(ProviderConfigEntity(existing?.id ?: UUID.randomUUID().toString(), name.ifBlank { "Custom provider" }, protocol, baseUrl, createdAt = existing?.createdAt ?: now, updatedAt = now), key.takeIf { it.isNotBlank() })
        }, enabled = name.isNotBlank() && baseUrl.startsWith("https://")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ModelDialog(existing: ModelConfigEntity, dismiss: () -> Unit, save: (ModelConfigEntity) -> Unit) {
    var id by remember { mutableStateOf(existing.modelId) }
    var name by remember { mutableStateOf(existing.displayName) }
    var vision by remember { mutableStateOf(existing.supportsVision) }
    var search by remember { mutableStateOf(existing.supportsSearch) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.model)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it }, label = { Text(stringResource(R.string.model_id)) }, enabled = existing.modelId.isBlank(), singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true)
                Row { Checkbox(vision, { vision = it }); Text(stringResource(R.string.supports_vision), Modifier.padding(top = 12.dp)) }
                Row { Checkbox(search, { search = it }); Text(stringResource(R.string.supports_search), Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { save(existing.copy(modelId = id.trim(), displayName = name.ifBlank { id.trim() }, supportsVision = vision, supportsSearch = search)) }, enabled = id.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
