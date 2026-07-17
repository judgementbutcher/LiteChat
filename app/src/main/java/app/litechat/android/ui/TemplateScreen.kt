package app.litechat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litechat.android.R
import app.litechat.android.data.model.PromptTemplateEntity
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

@Composable
fun TemplateScreen(viewModel: AppViewModel, openDrawer: (() -> Unit)?) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PromptTemplateEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    ScreenScaffold(
        stringResource(R.string.templates),
        openDrawer,
        floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Lucide.Plus, stringResource(R.string.templates)) } }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(templates, key = { it.id }) { template ->
                ListItem(
                    headlineContent = { Text(template.title) },
                    supportingContent = { Text(template.content, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth().clickable { editing = template }
                )
                HorizontalDivider()
            }
        }
    }
    if (adding) TemplateDialog(null, { adding = false }, { title, content -> viewModel.saveTemplate(null, title, content); adding = false }, null)
    editing?.let { template -> TemplateDialog(template, { editing = null }, { title, content -> viewModel.saveTemplate(template.id, title, content); editing = null }, { viewModel.deleteTemplate(template); editing = null }) }
}

@Composable
private fun TemplateDialog(existing: PromptTemplateEntity?, dismiss: () -> Unit, save: (String, String) -> Unit, delete: (() -> Unit)?) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.templates)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.title)) }, singleLine = true)
                OutlinedTextField(content, { content = it }, label = { Text(stringResource(R.string.prompt)) }, minLines = 5)
                if (delete != null) TextButton(delete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { save(title.trim(), content.trim()) }, enabled = title.isNotBlank() && content.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
