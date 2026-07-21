package app.litechat.android.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litechat.android.R
import app.litechat.android.data.model.*
import app.litechat.android.network.ChatAttachment
import com.composables.icons.lucide.*
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    var retryPickerFor by remember { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(conversationId, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var fullEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val markdownRenderer = rememberMarkdownRenderer()
    val modelName: (String, String) -> String = remember(models) {
        { providerId, modelId -> models.firstOrNull { it.providerId == providerId && it.modelId == modelId }?.displayName ?: modelId }
    }
    var initialScrollComplete by remember(conversationId) { mutableStateOf(false) }
    var scrollAfterMessageCount by remember(conversationId) { mutableStateOf<Int?>(null) }
    val atBottom by remember {
        derivedStateOf { !listState.canScrollForward }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(uris)
    }
    LaunchedEffect(conversationId, uiState.conversation?.id, uiState.messages.size) {
        if (uiState.conversation?.id == conversationId && uiState.messages.isNotEmpty()) {
            val requestedCount = scrollAfterMessageCount
            val requestReady = requestedCount != null && uiState.messages.size >= requestedCount
            val needsInitialPosition = !initialScrollComplete && requestedCount == null
            if (!requestReady && !needsInitialPosition) return@LaunchedEffect
            withFrameNanos { }
            listState.scrollToItem(uiState.messages.size)
            initialScrollComplete = true
            if (requestReady) scrollAfterMessageCount = null
        }
    }
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)
        ),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            conversation?.title?.ifBlank { stringResource(R.string.new_chat) } ?: "LiteChat",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val activeModel = models.firstOrNull {
                            it.providerId == conversation?.providerId && it.modelId == conversation.modelId
                        }
                        if (activeModel != null) Text(
                            activeModel.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { if (openDrawer != null) AccessibleIconButton(Lucide.Menu, stringResource(R.string.open_navigation), openDrawer) },
                actions = { AccessibleIconButton(Lucide.Ellipsis, stringResource(R.string.more_options), { settingsOpen = true }) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
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
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // The repository inserts one user row and one assistant row before
                        // streaming. Position once after both exist, then leave the viewport still.
                        scrollAfterMessageCount = uiState.messages.size + 2
                        viewModel.send(draft.text)
                        draft = TextFieldValue()
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.messages.isEmpty()) {
                val appear = remember { MutableTransitionState(false) }
                appear.targetState = true
                AnimatedVisibility(
                    visibleState = appear,
                    enter = fadeIn(tween(420)) + scaleIn(initialScale = 0.92f, animationSpec = tween(420)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) { Icon(Lucide.MessageCircle, null, Modifier.padding(18.dp).size(28.dp)) }
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(R.string.empty_chat), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        if (conversation?.providerId == null) TextButton(openProviders) { Text(stringResource(R.string.no_key_hint)) }
                    }
                }
            } else {
                val lastAssistantIndex = uiState.messages.indexOfLast { it.role == "assistant" }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    uiState.messages.forEachIndexed { index, message ->
                        item(key = message.id) {
                            Box(
                                Modifier.fillMaxWidth().animateItem(
                                    fadeInSpec = tween(220),
                                    fadeOutSpec = tween(140),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            ) {
                                MessageItem(
                                    message = message,
                                    variants = uiState.variantsByMessage[message.id].orEmpty(),
                                    attachments = uiState.attachmentsByMessage[message.id].orEmpty(),
                                    markdownRenderer = markdownRenderer,
                                    latestAssistant = message.role == "assistant" && index == lastAssistantIndex,
                                    modelName = modelName,
                                    onEdit = { editing = message },
                                    onRetry = { viewModel.retry(message.id) },
                                    onRetryWith = { retryPickerFor = message.id },
                                    onSelectVariant = { viewModel.selectVariant(message.id, it) }
                                )
                            }
                        }
                    }
                    item(key = "bottom-anchor") { Spacer(Modifier.fillMaxWidth().height(4.dp).testTag("chat-bottom-anchor")) }
                }
            }
            AnimatedVisibility(
                visible = !atBottom && uiState.messages.isNotEmpty(),
                enter = fadeIn(tween(160)) + scaleIn(tween(160)),
                exit = fadeOut(tween(160)) + scaleOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            ) {
                FilledTonalIconButton(onClick = {
                    scope.launch {
                        listState.animateScrollToItem(uiState.messages.size)
                    }
                }, modifier = Modifier.testTag("scroll-to-bottom")) {
                    Icon(Lucide.ArrowDown, stringResource(R.string.scroll_to_bottom))
                }
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
        select = { model -> viewModel.updateConversation(conversation.copy(providerId = model.providerId, modelId = model.modelId)); modelPickerOpen = false }
    )
    if (retryPickerFor != null && conversation != null) ModelPicker(
        conversation = conversation,
        models = models.filter { model -> providers.any { it.id == model.providerId && it.enabled } },
        dismiss = { retryPickerFor = null },
        select = { model -> retryPickerFor?.let { viewModel.retryWith(it, model) }; retryPickerFor = null }
    )
    if (fullEditor) FullScreenEditor(draft, { draft = it }, { fullEditor = false })
    editing?.let { message -> EditRegenerateDialog(message, { editing = null }) { value ->
        viewModel.editAndRegenerate(message.id, value)
        editing = null
    } }
}

@Composable
private fun MessageItem(
    message: MessageEntity,
    variants: List<ResponseVariantEntity>,
    attachments: List<AttachmentEntity>,
    markdownRenderer: Markwon,
    latestAssistant: Boolean,
    modelName: (String, String) -> String,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onRetryWith: () -> Unit,
    onSelectVariant: (String) -> Unit
) {
    val selected = variants.firstOrNull { it.id == message.selectedVariantId } ?: variants.lastOrNull()
    val content = if (message.role == "user") message.content else selected?.content.orEmpty()
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Center) {
        if (isUser) {
            LiquidGlassSurface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.large,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                shadowElevation = 4.dp,
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(.86f)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                    if (attachments.isNotEmpty()) SentAttachments(attachments)
                    if (content.isNotEmpty()) SelectionContainer { Text(content, style = MaterialTheme.typography.bodyLarge) }
                    UserActions(content, onEdit)
                }
            }
        } else {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                if (content.isNotEmpty()) {
                    MarkdownContent(content, markdownRenderer, Modifier.fillMaxWidth(), streaming = selected?.status == MessageStatus.STREAMING)
                }
                if (content.isEmpty() && selected?.status == MessageStatus.STREAMING) {
                    TypingIndicator()
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
                AnimatedVisibility(
                    visible = selected?.status != MessageStatus.STREAMING,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(120))
                ) {
                    Column {
                        if (variants.size > 1 && selected != null) {
                            Text(
                                modelName(selected.providerId, selected.modelId),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AssistantActions(content, selected, variants, latestAssistant, onRetry, onRetryWith, onSelectVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (reduceMotion) {
            repeat(3) { Box(Modifier.size(8.dp).background(color.copy(alpha = 0.6f), CircleShape)) }
        } else {
            val transition = rememberInfiniteTransition(label = "typing")
            repeat(3) { index ->
                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(650, delayMillis = index * 160, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$index"
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            val scale = 0.7f + progress * 0.5f
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.4f + progress * 0.6f
                        }
                        .background(color, CircleShape)
                )
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
    retryWith: () -> Unit,
    select: (String) -> Unit
) {
    val copy = rememberCopyAction(content)
    var menu by remember { mutableStateOf(false) }
    val index = variants.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
    val busy = selected?.status == MessageStatus.STREAMING
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (latest) {
            AccessibleIconButton(Lucide.Copy, stringResource(R.string.copy), copy, enabled = content.isNotEmpty())
            AccessibleIconButton(Lucide.RotateCcw, stringResource(R.string.retry), retry, enabled = !busy)
        }
        Box {
            AccessibleIconButton(Lucide.Ellipsis, stringResource(R.string.more_options), { menu = true })
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.copy)) }, { copy(); menu = false }, leadingIcon = { Icon(Lucide.Copy, null) })
                DropdownMenuItem({ Text(stringResource(R.string.retry)) }, { retry(); menu = false }, leadingIcon = { Icon(Lucide.RotateCcw, null) }, enabled = !busy)
                DropdownMenuItem({ Text(stringResource(R.string.retry_with)) }, { retryWith(); menu = false }, leadingIcon = { Icon(Lucide.Repeat, null) }, enabled = !busy)
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
private fun SentAttachments(attachments: List<AttachmentEntity>) {
    val images = attachments.filter { it.mimeType.startsWith("image/") }
    val files = attachments.filterNot { it.mimeType.startsWith("image/") }
    var preview by remember { mutableStateOf<AttachmentEntity?>(null) }
    if (images.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            images.forEach { image ->
                AttachmentThumbnail(
                    path = image.localPath,
                    contentDescription = image.displayName,
                    modifier = Modifier
                        .size(width = if (images.size == 1) 220.dp else 132.dp, height = 132.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { preview = image },
                    contentScale = ContentScale.Crop,
                    maxEdge = 512
                )
            }
        }
    }
    files.forEach { attachment ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.FileText, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    attachment.displayName,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
                if (attachment.truncated) Icon(Lucide.TriangleAlert, stringResource(R.string.truncated), Modifier.size(16.dp))
            }
        }
    }
    preview?.let { FullScreenImagePreview(it.localPath, it.displayName) { preview = null } }
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
    val borderColor by animateColorAsState(
        if (generating) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
        animationSpec = tween(280),
        label = "composer-border"
    )
    Box(
        Modifier.fillMaxWidth().background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (generating) 14.dp else 8.dp,
            shape = MaterialTheme.shapes.extraLarge,
            borderColor = borderColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 840.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = chooseModel,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Lucide.Bot, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(model?.displayName ?: stringResource(R.string.choose_model), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(4.dp)); Icon(Lucide.ChevronDown, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(
                        visible = model != null,
                        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)),
                        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120))
                    ) {
                        FilterChip(
                            selected = searchEnabled,
                            onClick = { toggleSearch(!searchEnabled) },
                            label = { Text(stringResource(R.string.search), maxLines = 1) },
                            leadingIcon = { Icon(Lucide.Search, null, Modifier.size(16.dp)) }
                        )
                    }
                }
                AnimatedVisibility(
                    visible = pending.isNotEmpty(),
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(160))
                ) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pending.forEachIndexed { index, item ->
                            PendingAttachment(item) { removeAttachment(index) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    TextField(
                        value = draft,
                        onValueChange = onDraft,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        placeholder = { Text(stringResource(R.string.message_hint)) },
                        minLines = 1,
                        maxLines = 5,
                        trailingIcon = {
                            if (draft.text.lines().size > 3) {
                                AccessibleIconButton(Lucide.Expand, stringResource(R.string.expand_editor), expand)
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    AnimatedContent(
                        targetState = generating,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.75f, animationSpec = tween(180))) togetherWith
                                (fadeOut(tween(120)) + scaleOut(targetScale = 0.75f, animationSpec = tween(120)))
                        },
                        label = "send-stop"
                    ) { isGenerating ->
                        if (isGenerating) {
                            Button(
                                onClick = stop,
                                modifier = Modifier.height(44.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Lucide.Square, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(R.string.stop))
                            }
                        } else {
                            FilledIconButton(
                                onClick = send,
                                enabled = draft.text.isNotBlank() || pending.isNotEmpty(),
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Lucide.ArrowUp, stringResource(R.string.send))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachment(item: ChatAttachment, remove: () -> Unit) {
    LiquidGlassSurface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    ) {
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
private fun AttachmentThumbnail(
    path: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier.size(40.dp),
    contentScale: ContentScale = ContentScale.Crop,
    maxEdge: Int = 128
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path, maxEdge) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription, modifier, contentScale = contentScale)
    else Box(modifier, contentAlignment = Alignment.Center) { Icon(Lucide.Image, contentDescription) }
}

@Composable
private fun FullScreenImagePreview(path: String, name: String, dismiss: () -> Unit) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + panChange
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier.fillMaxSize().transformable(transformState),
                contentAlignment = Alignment.Center
            ) {
                AttachmentThumbnail(
                    path = path,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                    contentScale = ContentScale.Fit,
                    maxEdge = 2048
                )
            }
            FilledTonalIconButton(
                onClick = dismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
            ) { Icon(Lucide.X, stringResource(R.string.close_preview)) }
        }
    }
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
