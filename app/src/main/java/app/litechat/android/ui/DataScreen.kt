package app.litechat.android.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.litechat.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataScreen(viewModel: AppViewModel, openDrawer: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingMarkdown by remember { mutableStateOf<String?>(null) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val value = pendingJson.orEmpty(); pendingJson = null
        uri?.let { scope.launch { withContext(Dispatchers.IO) { writeDocument(context, it, value) } } }
    }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val value = pendingMarkdown.orEmpty(); pendingMarkdown = null
        uri?.let { scope.launch { withContext(Dispatchers.IO) { writeDocument(context, it, value) } } }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(selected)?.bufferedReader()?.use { it.readText() }
                }
                if (raw != null) viewModel.importBackup(raw)
            }
        }
    }
    ScreenScaffold(stringResource(R.string.data_management), openDrawer) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.backup_summary))
            Button(onClick = { viewModel.exportBackup { text -> pendingJson = text; exportJson.launch("litechat-backup.json") } }) {
                Text(stringResource(R.string.export_backup))
            }
            OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "text/json")) }) { Text(stringResource(R.string.import_backup)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Button(onClick = { viewModel.exportMarkdown { text -> pendingMarkdown = text; exportMarkdown.launch("litechat-conversation.md") } }) {
                Text(stringResource(R.string.export_markdown))
            }
            Text(stringResource(R.string.backup_keys_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun writeDocument(context: Context, uri: Uri, value: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(value) }
}
