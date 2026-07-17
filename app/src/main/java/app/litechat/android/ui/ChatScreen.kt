package app.litechat.android.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litechat.android.R
import app.litechat.android.data.model.*
import app.litechat.android.network.ChatAttachment
import com.composables.icons.lucide.*
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: AppViewModel, conversationId: String, openDrawer: (() -> Unit)?, openProviders: () -> Unit) {
    LaunchedEffect(conversationId) { viewModel.openConversation(conversationId) }
    val uiState by viewModel.chatUiState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val pending by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val conversation = uiState.conversation
    var settingsOpen by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var draft by rememberSaveable(conversationId, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var fullEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var followOutput by remember(conversationId) { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.totalItemsCount == 0 || layout.visibleItemsInfo.lastOrNull()?.index.orEmptyIndex() >= layout.totalItemsCount - 2
        }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(uris)
    }
    val latestLength = uiState.variantsByMessage.values.sumOf { variants -> variants.sumOf { it.content.length } }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atBottom }.collect { (scrolling, bottom) ->
            if (scrolling) followOutput = bottom
        }
    }
    LaunchedEffect(uiState.messages.size, latestLength) {
        if (followOutput && uiState.messages.isNotEmpty()) listState.scrollToItem(uiState.messages.lastIndex, Int.MAX_VALUE)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title?.ifBlank { stringResource(R.string.new_chat) } ?: "LiteChat", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (openDrawer != null) AccessibleIconButton(Lucide.Menu, stringResource(R.string.open_navigation), openDrawer) },
                actions = { AccessibleIconButton(Lucide.Ellipsis, stringResource(R.string.more_options), { settingsOpen = true }) }
            )
        },
        bottomBar = {
            ChatComposer(
                draft = draft,
                onDraft = { draft = it },
                pending = pending,
                templates = templates,
                generating = generating,
                model = models.firstOrNull { it.providerId == conversation?.providerId && it.modelId == conversation.modelId },
                searchEnabled = conversation?.searchEnabled == true,
                attach = { attachmentLauncher.launch(arrayOf("image/*", "text/plain", "text/markdown", "application/pdf")) },
                removeAttachment = viewModel::removePending,
                chooseModel = { modelPickerOpen = true },
                toggleSearch = { enabled -> conversation?.let { viewModel.updateConversation(it.copy(searchEnabled = enabled)) } },
                expand = { fullEditor = true },
                stop = viewModel::stop,
                send = {
                    if (!generating && (draft.text.isNotBlank() || pending.isNotEmpty())) {
                        viewModel.send(draft.text)
                        draft = TextFieldValue()
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.empty_chat), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    if (conversation?.providerId == null) TextButton(openProviders) { Text(stringResource(R.string.no_key_hint)) }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    uiState.messages.forEachIndexed { index, message ->
                        item(key = message.id) {
                            Box(Modifier.fillMaxWidth().animateItem()) {
                                MessageItem(
                                    message = message,
                                    variants = uiState.variantsByMessage[message.id].orEmpty(),
                                    attachments = uiState.attachmentsByMessage[message.id].orEmpty(),
                                    latestAssistant = message.role == "assistant" && index == uiState.messages.indexOfLast { it.role == "assistant" },
                                    onEdit = { editing = message },
                                    onRetry = { viewModel.retry(message.id) },
                                    onSelectVariant = { viewModel.selectVariant(message.id, it) }
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = !atBottom && uiState.messages.isNotEmpty(),
                enter = fadeIn(tween(160)) + scaleIn(tween(160)),
                exit = fadeOut(tween(160)) + scaleOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            ) {
                FilledTonalIconButton(onClick = {
                    followOutput = true
                    scope.launch { listState.animateScrollToItem(uiState.messages.lastIndex, Int.MAX_VALUE) }
                }) { Icon(Lucide.ArrowDown, stringResource(R.string.scroll_to_bottom)) }
            }
        }
    }

    if (settingsOpen && conversation != null) {
        ConversationSettings(
            conversation = conversation,
            providers = providers,
            models = models,
            dismiss = { settingsOpen = false },
            save = { viewModel.updateConversation(it); settingsOpen = false }
        )
    }
    if (modelPickerOpen && conversation != null) ModelPicker(
        conversation = conversation,
        models = models.filter { model -> providers.any { it.id == model.providerId && it.enabled } },
        dismiss = { modelPickerOpen = false },
        select = { model -> viewModel.updateConversation(conversation.copy(providerId = model.providerId, modelId = model.modelId, searchEnabled = conversation.searchEnabled && model.supportsSearch)); modelPickerOpen = false }
    )
    if (fullEditor) FullScreenEditor(draft, { draft = it }, { fullEditor = false })
    editing?.let { message -> EditRegenerateDialog(message, { editing = null }) { value ->
        viewModel.editAndRegenerate(message.id, value)
        editing = null
    } }
}

private fun Int?.orEmptyIndex() = this ?: -1

@Composable
private fun MessageItem(
    message: MessageEntity,
    variants: List<ResponseVariantEntity>,
    attachments: List<AttachmentEntity>,
    latestAssistant: Boolean,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onSelectVariant: (String) -> Unit
) {
    val selected = variants.firstOrNull { it.id == message.selectedVariantId } ?: variants.lastOrNull()
    val content = if (message.role == "user") message.content else selected?.content.orEmpty()
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Center) {
        if (isUser) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(.84f)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                    SelectionContainer { Text(content, style = MaterialTheme.typography.bodyLarge) }
                    attachments.forEach { AttachmentRow(it) }
                    UserActions(content, onEdit)
                }
            }
        } else {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                if (content.isNotEmpty()) MarkdownContent(content)
                if (content.isEmpty() && selected?.status == MessageStatus.STREAMING) {
                    Text("•••", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                selected?.searchSummary?.let { SearchActivityRow(it) }
                if (selected?.status == MessageStatus.ERROR || selected?.status == MessageStatus.CANCELLED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.CircleAlert, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(selected.errorMessage ?: stringResource(R.string.generation_stopped), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onRetry) { Icon(Lucide.RotateCcw, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry)) }
                    }
                }
                AssistantActions(content, selected, variants, latestAssistant, onRetry, onSelectVariant)
            }
        }
    }
}

@Composable
private fun UserActions(content: String, onEdit: () -> Unit) {
    val copy = rememberCopyAction(content)
    val context = LocalContext.current
    Row {
        AccessibleIconButton(Lucide.Copy, stringResource(R.string.copy), copy, enabled = content.isNotEmpty())
        AccessibleIconButton(Lucide.Pencil, stringResource(R.string.edit_regenerate), onEdit)
        AccessibleIconButton(Lucide.Share2, stringResource(R.string.share), {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, content) }, null))
        })
    }
}

@Composable
private fun AssistantActions(
    content: String,
    selected: ResponseVariantEntity?,
    variants: List<ResponseVariantEntity>,
    latest: Boolean,
    retry: () -> Unit,
    select: (String) -> Unit
) {
    val copy = rememberCopyAction(content)
    var menu by remember { mutableStateOf(false) }
    val index = variants.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (latest) {
            AccessibleIconButton(Lucide.Copy, stringResource(R.string.copy), copy, enabled = content.isNotEmpty())
            AccessibleIconButton(Lucide.RotateCcw, stringResource(R.string.retry), retry, enabled = selected?.status != MessageStatus.STREAMING)
        }
        Box {
            AccessibleIconButton(Lucide.Ellipsis, stringResource(R.string.more_options), { menu = true })
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.copy)) }, { copy(); menu = false }, leadingIcon = { Icon(Lucide.Copy, null) })
                DropdownMenuItem({ Text(stringResource(R.string.retry)) }, { retry(); menu = false }, leadingIcon = { Icon(Lucide.RotateCcw, null) }, enabled = selected?.status != MessageStatus.STREAMING)
            }
        }
        if (variants.size > 1) {
            AccessibleIconButton(Lucide.ChevronLeft, stringResource(R.string.previous_response), { select(variants[index - 1].id) }, enabled = index > 0)
            Text("${index + 1}/${variants.size}", style = MaterialTheme.typography.labelMedium)
            AccessibleIconButton(Lucide.ChevronRight, stringResource(R.string.next_response), { select(variants[index + 1].id) }, enabled = index < variants.lastIndex)
        }
    }
}

@Composable
private fun SearchActivityRow(summary: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Icon(Lucide.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.native_search)); Spacer(Modifier.width(4.dp))
            Icon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, null, Modifier.size(18.dp))
        }
        AnimatedVisibility(expanded) { Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentEntity) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (attachment.mimeType.startsWith("image/")) Lucide.Image else Lucide.File, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(attachment.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        if (attachment.truncated) { Spacer(Modifier.width(4.dp)); Icon(Lucide.TriangleAlert, stringResource(R.string.truncated), Modifier.size(16.dp)) }
    }
}

@Composable
private fun MarkdownContent(content: String) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content.split("```").forEachIndexed { index, block ->
                if (block.isBlank()) return@forEachIndexed
                if (index % 2 == 1) CodeBlock(block) else block.split("$$").forEachIndexed { mathIndex, part ->
                    if (part.isBlank()) return@forEachIndexed
                    if (mathIndex % 2 == 1) LatexBlock(part) else Markdown(content = stripRemoteImages(part))
                }
            }
        }
    }
}

private fun stripRemoteImages(value: String): String = value.replace(Regex("!\\[([^]]*)]\\(https?://[^)]+\\)"), "[$1]")

@Composable
private fun CodeBlock(raw: String) {
    val language = raw.lineSequence().firstOrNull().orEmpty().trim()
    val code = if ('\n' in raw) raw.substringAfter('\n').trimEnd() else raw
    val copy = rememberCopyAction(code)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(language.ifBlank { stringResource(R.string.code) }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                AccessibleIconButton(Lucide.Copy, stringResource(R.string.copy), copy)
            }
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)
            )
        }
    }
}

@Composable
private fun LatexBlock(value: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(renderLatex(value), fontFamily = FontFamily.Serif, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp))
    }
}

private fun renderLatex(value: String): String {
    var text = value.trim()
    mapOf("\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\theta" to "θ", "\\lambda" to "λ", "\\pi" to "π", "\\sum" to "∑", "\\int" to "∫", "\\sqrt" to "√").forEach { (latex, symbol) -> text = text.replace(latex, symbol) }
    return Regex("\\\\frac\\{([^{}]+)}\\{([^{}]+)}").replace(text) { "${it.groupValues[1]}⁄${it.groupValues[2]}" }.replace("{", "").replace("}", "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatComposer(
    draft: TextFieldValue,
    onDraft: (TextFieldValue) -> Unit,
    pending: List<ChatAttachment>,
    templates: List<PromptTemplateEntity>,
    generating: Boolean,
    model: ModelConfigEntity?,
    searchEnabled: Boolean,
    attach: () -> Unit,
    removeAttachment: (Int) -> Unit,
    chooseModel: () -> Unit,
    toggleSearch: (Boolean) -> Unit,
    expand: () -> Unit,
    stop: () -> Unit,
    send: () -> Unit
) {
    Box(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).widthIn(max = 840.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(chooseModel) {
                        Text(model?.displayName ?: stringResource(R.string.choose_model), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(4.dp)); Icon(Lucide.ChevronDown, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (model?.supportsSearch == true) {
                        Icon(Lucide.Search, null, Modifier.size(18.dp))
                        Switch(searchEnabled, toggleSearch, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (pending.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pending.forEachIndexed { index, item -> PendingAttachment(item) { removeAttachment(index) } }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        AccessibleIconButton(Lucide.Plus, stringResource(R.string.attach_files), { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.attach)) }, { menu = false; attach() }, leadingIcon = { Icon(Lucide.Paperclip, null) })
                            templates.forEach { template -> DropdownMenuItem(
                                text = { Text(template.title) },
                                onClick = { onDraft(draft.copy(text = if (draft.text.isBlank()) template.content else "${draft.text}\n${template.content}")); menu = false },
                                leadingIcon = { Icon(Lucide.FileText, null) }
                            ) }
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraft,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.message_hint)) },
                        minLines = 1,
                        maxLines = 6,
                        trailingIcon = { if (draft.text.lines().size > 3) AccessibleIconButton(Lucide.Expand, stringResource(R.string.expand_editor), expand) }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = if (generating) stop else send,
                        enabled = generating || draft.text.isNotBlank() || pending.isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) { Icon(if (generating) Lucide.Square else Lucide.ArrowUp, stringResource(if (generating) R.string.stop else R.string.send)) }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachment(item: ChatAttachment, remove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Row(Modifier.height(52.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.mimeType.startsWith("image/")) AttachmentThumbnail(item.localPath) else Icon(Lucide.File, null)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.widthIn(max = 150.dp)) {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                if (item.truncated) Text(stringResource(R.string.truncated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            AccessibleIconButton(Lucide.X, stringResource(R.string.remove_attachment, item.displayName), remove)
        }
    }
}

@Composable
private fun AttachmentThumbnail(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.size(40.dp)) else Icon(Lucide.Image, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(conversation: ConversationEntity, models: List<ModelConfigEntity>, dismiss: () -> Unit, select: (ModelConfigEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.choose_model), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), singleLine = true, leadingIcon = { Icon(Lucide.Search, null) }, placeholder = { Text(stringResource(R.string.search_models)) })
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                models.filter { it.enabled && (query.isBlank() || it.displayName.contains(query, true) || it.modelId.contains(query, true)) }.forEach { model ->
                    item(key = "${model.providerId}/${model.modelId}") {
                        ListItem(
                            headlineContent = { Text(model.displayName) },
                            supportingContent = { Text(model.modelId) },
                            leadingContent = { RadioButton(conversation.providerId == model.providerId && conversation.modelId == model.modelId, { select(model) }) },
                            modifier = Modifier.fillMaxWidth().clickable { select(model) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationSettings(conversation: ConversationEntity, providers: List<ProviderConfigEntity>, models: List<ModelConfigEntity>, dismiss: () -> Unit, save: (ConversationEntity) -> Unit) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    var title by remember { mutableStateOf(conversation.title) }
    var system by remember { mutableStateOf(conversation.systemPrompt) }
    val content: @Composable ColumnScope.() -> Unit = {
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.rename)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(system, { system = it }, label = { Text(stringResource(R.string.system_prompt)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(dismiss) { Text(stringResource(R.string.cancel)) }
            TextButton({ save(conversation.copy(title = title.ifBlank { conversation.title }, systemPrompt = system)) }) { Text(stringResource(R.string.save)) }
        }
    }
    if (compact) ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    } else AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.conversation_settings)) },
        text = { Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenEditor(value: TextFieldValue, onValue: (TextFieldValue) -> Unit, dismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = { TopAppBar(title = { Text(stringResource(R.string.message_hint)) }, navigationIcon = { AccessibleIconButton(Lucide.ArrowLeft, stringResource(R.string.done), dismiss) }) }
            ) { padding -> OutlinedTextField(value, onValue, Modifier.fillMaxSize().padding(padding).padding(16.dp)) }
        }
    }
}

@Composable
private fun EditRegenerateDialog(message: MessageEntity, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var value by remember(message.id) { mutableStateOf(message.content) }
    var confirmed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.edit_regenerate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value, { value = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
                if (confirmed) Text(stringResource(R.string.edit_regenerate_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton({ if (confirmed) confirm(value) else confirmed = true }, enabled = value.isNotBlank()) { Text(if (confirmed) stringResource(R.string.edit_regenerate) else stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
