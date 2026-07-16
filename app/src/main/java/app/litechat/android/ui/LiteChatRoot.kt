package app.litechat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.litechat.android.R
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{conversationId}"
    const val PROVIDERS = "providers"
    const val TEMPLATES = "templates"
    const val DATA = "data"
    const val ABOUT = "about"
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Surface(tonalElevation = 1.dp, modifier = Modifier.width(300.dp).fillMaxHeight()) {
                    ConversationSidebar(viewModel, nav)
                }
                Box(Modifier.weight(1f)) { AppNavHost(viewModel, nav, null); SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(Modifier.width(310.dp)) {
                        ConversationSidebar(viewModel, nav) { scope.launch { drawerState.close() } }
                    }
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

@Composable
private fun AppNavHost(viewModel: AppViewModel, nav: NavHostController, openDrawer: (() -> Unit)?) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
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
        composable(Routes.ABOUT) { AboutScreen(viewModel, openDrawer) }
    }
}

@Composable
private fun ConversationSidebar(viewModel: AppViewModel, nav: NavHostController, afterNavigate: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    val conversations by remember(query) { viewModel.searchConversations(query) }.collectAsStateWithLifecycle(initialValue = emptyList())
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LiteChat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { viewModel.createConversation { nav.navigate(Routes.chat(it)); afterNavigate() } }) {
                Text("+")
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.new_chat))
            }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_conversations)) }
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(conversations, key = { it.id }) { conversation ->
                TextButton(
                    onClick = { nav.navigate(Routes.chat(conversation.id)) { launchSingleTop = true }; afterNavigate() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(conversation.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        HorizontalDivider()
        SidebarLink(stringResource(R.string.providers)) { nav.navigate(Routes.PROVIDERS); afterNavigate() }
        SidebarLink(stringResource(R.string.templates)) { nav.navigate(Routes.TEMPLATES); afterNavigate() }
        SidebarLink(stringResource(R.string.data_management)) { nav.navigate(Routes.DATA); afterNavigate() }
        SidebarLink(stringResource(R.string.about)) { nav.navigate(Routes.ABOUT); afterNavigate() }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SidebarLink(label: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyHomeScreen(openDrawer: (() -> Unit)?, create: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("LiteChat") },
            navigationIcon = { if (openDrawer != null) TextButton(openDrawer, Modifier.semantics { contentDescription = "Open conversations" }) { Text("☰") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.empty_chat), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Button(create) { Text(stringResource(R.string.new_chat)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(title: String, openDrawer: (() -> Unit)?, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { if (openDrawer != null) TextButton(openDrawer, Modifier.semantics { contentDescription = "Open conversations" }) { Text("☰") } }
        )
    }, content = content)
}
