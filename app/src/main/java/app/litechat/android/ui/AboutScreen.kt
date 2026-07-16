package app.litechat.android.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litechat.android.BuildConfig
import app.litechat.android.R
import app.litechat.android.data.settings.ThemeMode

@Composable
fun AboutScreen(viewModel: AppViewModel, openDrawer: (() -> Unit)?) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.about), openDrawer) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("LiteChat ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.privacy_summary))
            HorizontalDivider()
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode -> FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setTheme(mode) },
                    label = { Text(when (mode) { ThemeMode.SYSTEM -> stringResource(R.string.follow_system); ThemeMode.LIGHT -> stringResource(R.string.light); ThemeMode.DARK -> stringResource(R.string.dark) }) }
                ) }
            }
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to stringResource(R.string.follow_system), "en" to "English", "zh-CN" to "简体中文").forEach { (tag, label) ->
                    FilterChip(selected = settings.languageTag == tag, onClick = {
                        viewModel.setLanguage(tag)
                        AppCompatDelegate.setApplicationLocales(if (tag == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                    }, label = { Text(label) })
                }
            }
            HorizontalDivider()
            Text("Default generation parameters", style = MaterialTheme.typography.titleMedium)
            Text("Temperature · ${"%.1f".format(settings.temperature)}")
            Slider(settings.temperature, { viewModel.setParameters(it, settings.topP) }, valueRange = 0f..2f, steps = 19)
            Text("Top P · ${"%.2f".format(settings.topP)}")
            Slider(settings.topP, { viewModel.setParameters(settings.temperature, it) }, valueRange = 0f..1f, steps = 19)
            if (viewModel.updateCheckEnabled) {
                HorizontalDivider()
                OutlinedButton(viewModel::checkForUpdate) { Text("Check GitHub Releases") }
            }
            Text("MIT License · Source-first Android app", style = MaterialTheme.typography.bodySmall)
        }
    }
}
