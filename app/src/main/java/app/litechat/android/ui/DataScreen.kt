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

@Composable
fun DataScreen(viewModel: AppViewModel, openDrawer: (() -> Unit)?) {
    val context = LocalContext.current
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingMarkdown by remember { mutableStateOf<String?>(null) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeDocument(context, it, pendingJson.orEmpty()) }; pendingJson = null
    }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let { writeDocument(context, it, pendingMarkdown.orEmpty()) }; pendingMarkdown = null
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected -> context.contentResolver.openInputStream(selected)?.bufferedReader()?.use { viewModel.importBackup(it.readText()) } }
    }
    ScreenScaffold(stringResource(R.string.data_management), openDrawer) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Backup schema v1 includes provider settings (without keys), models, conversations, response versions, attachments, and templates.")
            Button(onClick = { viewModel.exportBackup { text -> pendingJson = text; exportJson.launch("litechat-backup.json") } }) {
                Text(stringResource(R.string.export_backup))
            }
            OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "text/json")) }) { Text(stringResource(R.string.import_backup)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Button(onClick = { viewModel.exportMarkdown { text -> pendingMarkdown = text; exportMarkdown.launch("litechat-conversation.md") } }) {
                Text(stringResource(R.string.export_markdown))
            }
            Text("Import never reads or replaces encrypted API keys.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun writeDocument(context: Context, uri: Uri, value: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(value) }
}
