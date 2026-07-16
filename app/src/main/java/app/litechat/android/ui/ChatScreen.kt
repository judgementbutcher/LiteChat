package app.litechat.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litechat.android.R
import app.litechat.android.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: AppViewModel, conversationId: String, openDrawer: (() -> Unit)?, openProviders: () -> Unit) {
    LaunchedEffect(conversationId) { viewModel.openConversation(conversationId) }
    val conversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val variants by viewModel.variants.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val pending by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    var draft by rememberSaveable(conversationId) { mutableStateOf("") }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    val listState = rememberLazyListState()
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(uris)
    }
    LaunchedEffect(messages.size, variants.sumOf { it.content.length }) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title ?: "LiteChat", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (openDrawer != null) TextButton(openDrawer, Modifier.semantics { contentDescription = "Open conversations" }) { Text("☰") } },
                actions = {
                    TextButton(onClick = { settingsOpen = true }, modifier = Modifier.semantics { contentDescription = "Conversation settings" }) { Text("⚙", modifier = Modifier.padding(horizontal = 4.dp)) }
                }
            )
        },
        bottomBar = {
            ChatComposer(
                draft = draft,
                onDraft = { draft = it },
                pending = pending,
                templates = templates,
                generating = generating,
                attach = { attachmentLauncher.launch(arrayOf("image/*", "text/plain", "text/markdown", "application/pdf")) },
                removeAttachment = viewModel::removePending,
                stop = viewModel::stop,
                send = { if (draft.isNotBlank() || pending.isNotEmpty()) { viewModel.send(draft); draft = "" } }
            )
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.empty_chat), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                if (conversation?.providerId == null) TextButton(openProviders) { Text(stringResource(R.string.no_key_hint)) }
            }
        } else LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            messages.forEach { message ->
                item(key = message.id) {
                    MessageCard(
                        message = message,
                        variants = variants.filter { it.messageId == message.id },
                        attachments = attachments.filter { it.messageId == message.id },
                        onEdit = { editing = message },
                        onRetry = { viewModel.retry(message.id) },
                        onSelectVariant = { viewModel.selectVariant(message.id, it) }
                    )
                }
            }
        }
    }

    if (settingsOpen && conversation != null) ConversationSettingsDialog(
        conversation = conversation!!,
        providers = providers,
        models = models,
        onDismiss = { settingsOpen = false },
        onSave = { viewModel.updateConversation(it); settingsOpen = false },
        onDelete = { viewModel.deleteConversation(conversation!!) { settingsOpen = false } }
    )
    editing?.let { message ->
        var value by remember(message.id) { mutableStateOf(message.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.edit)) },
            text = { OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = { TextButton(onClick = { viewModel.editMessage(message.id, value); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun MessageCard(
    message: MessageEntity,
    variants: List<ResponseVariantEntity>,
    attachments: List<AttachmentEntity>,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onSelectVariant: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val selected = variants.firstOrNull { it.id == message.selectedVariantId } ?: variants.lastOrNull()
    val content = if (message.role == "user") message.content else selected?.content.orEmpty()
    val selectedIndex = variants.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(if (isUser) .88f else 1f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (isUser) "You" else "LiteChat", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (content.isNotEmpty()) { Spacer(Modifier.height(7.dp)); MarkdownContent(content) }
                if (!isUser && content.isEmpty() && selected?.status == MessageStatus.STREAMING) {
                    Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                attachments.forEach { attachment ->
                    Spacer(Modifier.height(6.dp))
                    AssistChip(onClick = {}, enabled = false, label = {
                        Text("📎 ${attachment.displayName}${if (attachment.truncated) " · truncated" else ""}")
                    })
                }
                selected?.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                selected?.searchSummary?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("⌕ $it", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(content)) }, enabled = content.isNotEmpty()) { Text(stringResource(R.string.copy)) }
                    if (isUser) TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                    else {
                        TextButton(onClick = onRetry, enabled = selected?.status != MessageStatus.STREAMING) { Text(stringResource(R.string.retry)) }
                        if (variants.size > 1) {
                            TextButton(onClick = { if (selectedIndex > 0) onSelectVariant(variants[selectedIndex - 1].id) }, enabled = selectedIndex > 0, modifier = Modifier.semantics { contentDescription = "Previous response version" }) { Text("‹") }
                            Text("${selectedIndex + 1}/${variants.size}", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { if (selectedIndex < variants.lastIndex) onSelectVariant(variants[selectedIndex + 1].id) }, enabled = selectedIndex < variants.lastIndex, modifier = Modifier.semantics { contentDescription = "Next response version" }) { Text("›") }
                        }
                    }
                    selected?.let { Text(it.status.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownContent(content: String) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content.split("```").forEachIndexed { index, block ->
                if (block.isBlank()) return@forEachIndexed
                if (index % 2 == 1) {
                    val code = block.substringAfter('\n', block)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(
                            code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
                        )
                    }
                } else block.split("$$").forEachIndexed { mathIndex, part ->
                    if (part.isBlank()) return@forEachIndexed
                    if (mathIndex % 2 == 1) Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(renderLatex(part), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp))
                    } else Text(markdownAnnotated(part), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun markdownAnnotated(value: String): AnnotatedString = buildAnnotatedString {
    value.lines().forEachIndexed { lineIndex, source ->
        val heading = source.startsWith("#")
        var line = source.trimStart('#').trimStart()
        if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.drop(2)
        if (heading) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        var cursor = 0
        val bold = Regex("\\*\\*(.+?)\\*\\*")
        bold.findAll(line).forEach { match ->
            append(line.substring(cursor, match.range.first))
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(match.groupValues[1]); pop()
            cursor = match.range.last + 1
        }
        append(line.substring(cursor))
        if (heading) pop()
        if (lineIndex < value.lines().lastIndex) append('\n')
    }
}

private fun renderLatex(value: String): String {
    var text = value.trim()
    val symbols = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\theta" to "θ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\pi" to "π", "\\sigma" to "σ", "\\phi" to "φ", "\\omega" to "ω",
        "\\times" to "×", "\\cdot" to "·", "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠", "\\infty" to "∞",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\sqrt" to "√"
    )
    symbols.forEach { (latex, symbol) -> text = text.replace(latex, symbol) }
    text = Regex("\\\\frac\\{([^{}]+)}\\{([^{}]+)}").replace(text) { "${it.groupValues[1]}⁄${it.groupValues[2]}" }
    return text.replace("{", "").replace("}", "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatComposer(
    draft: String,
    onDraft: (String) -> Unit,
    pending: List<app.litechat.android.network.ChatAttachment>,
    templates: List<PromptTemplateEntity>,
    generating: Boolean,
    attach: () -> Unit,
    removeAttachment: (Int) -> Unit,
    stop: () -> Unit,
    send: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (pending.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pending.forEachIndexed { index, item -> InputChip(
                    selected = false, onClick = { removeAttachment(index) },
                    label = { Text("${item.displayName}${if (item.truncated) " ⚠" else ""}", maxLines = 1) }, trailingIcon = { Text("×") }
                ) }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                TextButton(onClick = attach, enabled = !generating, modifier = Modifier.sizeIn(minWidth = 48.dp).semantics { contentDescription = "Attach files" }) { Text("📎") }
                var templateMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { templateMenu = true }, enabled = templates.isNotEmpty() && !generating, modifier = Modifier.semantics { contentDescription = "Insert prompt template" }) { Text("⌘") }
                    DropdownMenu(templateMenu, { templateMenu = false }) {
                        templates.forEach { template -> DropdownMenuItem(
                            text = { Text(template.title) },
                            onClick = { onDraft(if (draft.isBlank()) template.content else "$draft\n${template.content}"); templateMenu = false }
                        ) }
                    }
                }
                OutlinedTextField(
                    value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.message_hint)) }, maxLines = 6,
                    enabled = !generating
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = if (generating) stop else send, enabled = generating || draft.isNotBlank() || pending.isNotEmpty()) {
                    Text(if (generating) stringResource(R.string.stop) else stringResource(R.string.send))
                }
            }
        }
    }
}

@Composable
private fun ConversationSettingsDialog(
    conversation: ConversationEntity,
    providers: List<ProviderConfigEntity>,
    models: List<ModelConfigEntity>,
    onDismiss: () -> Unit,
    onSave: (ConversationEntity) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(conversation.title) }
    var system by remember { mutableStateOf(conversation.systemPrompt) }
    var providerId by remember { mutableStateOf(conversation.providerId) }
    var modelId by remember { mutableStateOf(conversation.modelId) }
    var search by remember { mutableStateOf(conversation.searchEnabled) }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val availableModels = models.filter { it.providerId == providerId && it.enabled }
    val chosenModel = availableModels.firstOrNull { it.modelId == modelId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.rename)) }, singleLine = true)
                OutlinedTextField(system, { system = it }, label = { Text(stringResource(R.string.system_prompt)) }, minLines = 2)
                Box {
                    OutlinedButton(onClick = { providerMenu = true }, Modifier.fillMaxWidth()) {
                        Text(providers.firstOrNull { it.id == providerId }?.name ?: stringResource(R.string.provider), Modifier.fillMaxWidth())
                    }
                    DropdownMenu(providerMenu, { providerMenu = false }) {
                        providers.filter { it.enabled }.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = {
                            providerId = p.id; modelId = models.firstOrNull { it.providerId == p.id && it.enabled }?.modelId; search = false; providerMenu = false
                        }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { modelMenu = true }, enabled = availableModels.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(chosenModel?.displayName ?: stringResource(R.string.model), Modifier.fillMaxWidth())
                    }
                    DropdownMenu(modelMenu, { modelMenu = false }) {
                        availableModels.forEach { model -> DropdownMenuItem(text = { Text(model.displayName) }, onClick = { modelId = model.modelId; search = false; modelMenu = false }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(search, { search = it }, enabled = chosenModel?.supportsSearch == true)
                    Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.native_search))
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(conversation.copy(title = title.ifBlank { "New chat" }, systemPrompt = system, providerId = providerId, modelId = modelId, searchEnabled = search)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
