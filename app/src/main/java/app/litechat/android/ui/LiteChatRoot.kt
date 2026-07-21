package app.litechat.android.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.litechat.android.R
import app.litechat.android.data.model.ConversationEntity
import com.composables.icons.lucide.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{conversationId}"
    const val PROVIDERS = "providers"
    const val TEMPLATES = "templates"
    const val DATA = "data"
    const val SETTINGS = "settings"
    const val ARCHIVED = "archived"
    fun chat(id: String) = "chat/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiteChatRoot(viewModel: AppViewModel) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }

    CompositionLocalProvider(
        LocalAppSnackbarHostState provides snackbar
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sidebarWidth = when {
                        maxWidth >= 840.dp -> 300.dp
                        maxWidth >= 600.dp -> 280.dp
                        else -> null
                    }
                    if (sidebarWidth != null) {
                        Row(Modifier.fillMaxSize()) {
                            LiquidGlassSurface(
                                color = MaterialTheme.colorScheme.surface,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                                shadowElevation = 18.dp,
                                modifier = Modifier.width(sidebarWidth).fillMaxHeight()
                            ) { ConversationSidebar(viewModel, nav) }
                            Box(Modifier.weight(1f)) {
                                AppNavHost(viewModel, nav, null)
                                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    } else {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    modifier = Modifier.widthIn(max = 320.dp),
                                    drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    drawerTonalElevation = 0.dp
                                ) { ConversationSidebar(viewModel, nav) { scope.launch { drawerState.close() } } }
                            }
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                AppNavHost(viewModel, nav) { scope.launch { drawerState.open() } }
                                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(viewModel: AppViewModel, nav: NavHostController, openDrawer: (() -> Unit)?) {
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { 16 } },
        exitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -16 } },
        popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -16 } },
        popExitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { 16 } }
    ) {
        composable(Routes.HOME) {
            EmptyHomeScreen(openDrawer) { viewModel.createConversation { nav.navigate(Routes.chat(it)) } }
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
            val id = requireNotNull(entry.arguments?.getString("conversationId"))
            ChatScreen(viewModel, id, openDrawer) { nav.navigate(Routes.PROVIDERS) }
        }
        composable(Routes.PROVIDERS) { ProviderScreen(viewModel, openDrawer) }
        composable(Routes.TEMPLATES) { TemplateScreen(viewModel, openDrawer) }
        composable(Routes.DATA) { DataScreen(viewModel, openDrawer) }
        composable(Routes.SETTINGS) { AboutScreen(viewModel, openDrawer) }
        composable(Routes.ARCHIVED) { ArchivedScreen(viewModel, openDrawer) }
    }
}

@Composable
private fun ConversationSidebar(viewModel: AppViewModel, nav: NavHostController, afterNavigate: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val conversations by remember { viewModel.searchConversations(snapshotFlow { query }) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sections = remember(conversations) { groupConversations(conversations) }
    val backStack by nav.currentBackStackEntryAsState()
    val selectedId = backStack?.arguments?.getString("conversationId")
    var rename by remember { mutableStateOf<ConversationEntity?>(null) }
    var deleting by remember { mutableStateOf<ConversationEntity?>(null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassSurface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = MaterialTheme.shapes.medium,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
            ) {
                Icon(Lucide.MessageCircle, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Text("LiteChat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            AccessibleIconButton(Lucide.Search, stringResource(R.string.search), { searchVisible = !searchVisible })
            AccessibleIconButton(Lucide.Plus, stringResource(R.string.new_chat), {
                viewModel.createConversation { nav.navigate(Routes.chat(it)); afterNavigate() }
            })
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = searchVisible,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
        ) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                placeholder = { Text(stringResource(R.string.search_conversations)) },
                trailingIcon = {
                    if (query.isNotEmpty()) AccessibleIconButton(Lucide.X, stringResource(R.string.clear_search), { query = "" })
                }
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            sections.forEach { section ->
                item(key = "section-${section.group}") {
                    Text(
                        when (section.group) {
                            ConversationGroup.PINNED -> stringResource(R.string.pinned)
                            ConversationGroup.TODAY -> stringResource(R.string.today)
                            ConversationGroup.LAST_SEVEN_DAYS -> stringResource(R.string.last_seven_days)
                            ConversationGroup.OLDER -> stringResource(R.string.older)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(section.conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selected = selectedId == conversation.id,
                        onClick = { nav.navigate(Routes.chat(conversation.id)) { launchSingleTop = true }; afterNavigate() },
                        onRename = { rename = conversation },
                        onPin = { viewModel.setPinned(conversation, conversation.pinnedAt == null) },
                        onArchive = {
                            viewModel.setArchived(conversation, true) {
                                if (selectedId == conversation.id) nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                            }
                        },
                        onDelete = { deleting = conversation }
                    )
                }
            }
        }
        HorizontalDivider()
        SidebarLink(Lucide.Archive, stringResource(R.string.archived)) { nav.navigate(Routes.ARCHIVED); afterNavigate() }
        SidebarLink(Lucide.Server, stringResource(R.string.providers)) { nav.navigate(Routes.PROVIDERS); afterNavigate() }
        SidebarLink(Lucide.FileText, stringResource(R.string.templates)) { nav.navigate(Routes.TEMPLATES); afterNavigate() }
        SidebarLink(Lucide.Database, stringResource(R.string.data_management)) { nav.navigate(Routes.DATA); afterNavigate() }
        SidebarLink(Lucide.Settings, stringResource(R.string.about)) { nav.navigate(Routes.SETTINGS); afterNavigate() }
    }

    rename?.let { conversation -> RenameDialog(conversation, { rename = null }) {
        viewModel.renameConversation(conversation, it); rename = null
    } }
    deleting?.let { conversation -> DeleteConversationDialog(conversation, { deleting = null }) {
        viewModel.deleteConversation(conversation) {
            deleting = null
            if (selectedId == conversation.id) nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
        }
    } }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        label = {
            Column {
                Text(conversation.title.ifBlank { stringResource(R.string.new_chat) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    conversationTimeLabel(conversation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        icon = { if (conversation.pinnedAt != null) Icon(Lucide.Pin, null, Modifier.size(16.dp)) },
        badge = {
            Box {
                AccessibleIconButton(Lucide.Ellipsis, stringResource(R.string.more_options), { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; onRename() }, leadingIcon = { Icon(Lucide.Pencil, null) })
                    DropdownMenuItem({ Text(stringResource(if (conversation.pinnedAt == null) R.string.pin else R.string.unpin)) }, { menu = false; onPin() }, leadingIcon = { Icon(Lucide.Pin, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.archive)) }, { menu = false; onArchive() }, leadingIcon = { Icon(Lucide.Archive, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; onDelete() }, leadingIcon = { Icon(Lucide.Trash2, null) })
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedContainerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun conversationTimeLabel(timestamp: Long): String = when (relativeConversationTime(timestamp)) {
    RelativeConversationTime.TODAY -> stringResource(R.string.today)
    RelativeConversationTime.YESTERDAY -> stringResource(R.string.yesterday)
    RelativeConversationTime.THIS_WEEK -> stringResource(R.string.this_week)
    RelativeConversationTime.OLDER -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
}

@Composable
private fun SidebarLink(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null, Modifier.size(20.dp)) },
        label = { Text(label) },
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyHomeScreen(openDrawer: (() -> Unit)?, create: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LiteChat") },
                navigationIcon = { if (openDrawer != null) AccessibleIconButton(Lucide.Menu, stringResource(R.string.open_navigation), openDrawer) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LiquidGlassSurface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.extraLarge,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                shadowElevation = 18.dp
            ) {
                Icon(
                    Lucide.MessageCircle,
                    null,
                    Modifier.padding(24.dp).size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.empty_chat), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.privacy_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 480.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(create, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                Icon(Lucide.Plus, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_chat))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(
    title: String,
    openDrawer: (() -> Unit)?,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (openDrawer != null) AccessibleIconButton(Lucide.Menu, stringResource(R.string.open_navigation), openDrawer) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        floatingActionButton = floatingActionButton,
        content = content
    )
}

@Composable
private fun RenameDialog(conversation: ConversationEntity, dismiss: () -> Unit, save: (String) -> Unit) {
    var value by remember(conversation.id) { mutableStateOf(conversation.title) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true) },
        confirmButton = { TextButton({ save(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun DeleteConversationDialog(conversation: ConversationEntity, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_confirm, conversation.title.ifBlank { stringResource(R.string.new_chat) })) },
        confirmButton = { TextButton(confirm) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ArchivedScreen(viewModel: AppViewModel, openDrawer: (() -> Unit)?) {
    var query by rememberSaveable { mutableStateOf("") }
    val conversations by remember { viewModel.archivedConversations(snapshotFlow { query }) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var deleting by remember { mutableStateOf<ConversationEntity?>(null) }
    ScreenScaffold(stringResource(R.string.archived), openDrawer) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).widthIn(max = 840.dp).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_conversations)) },
                leadingIcon = { Icon(Lucide.Search, null) }
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    LiquidGlassSurface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).animateItem()
                    ) {
                        ListItem(
                            headlineContent = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(conversationTimeLabel(conversation.updatedAt)) },
                            trailingContent = {
                                Row {
                                    AccessibleIconButton(Lucide.ArchiveRestore, stringResource(R.string.restore), { viewModel.setArchived(conversation, false) })
                                    AccessibleIconButton(Lucide.Trash2, stringResource(R.string.delete_forever), { deleting = conversation })
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
    deleting?.let { conversation -> DeleteConversationDialog(conversation, { deleting = null }) {
        viewModel.deleteConversation(conversation) { deleting = null }
    } }
}
