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
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.dynamic_color), modifier = Modifier.weight(1f))
                Switch(settings.dynamicColor, viewModel::setDynamicColor)
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
            Text(stringResource(R.string.default_parameters), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.temperature, settings.temperature))
            Slider(settings.temperature, { viewModel.setParameters(it, settings.topP) }, valueRange = 0f..2f, steps = 19)
            Text(stringResource(R.string.top_p, settings.topP))
            Slider(settings.topP, { viewModel.setParameters(settings.temperature, it) }, valueRange = 0f..1f, steps = 19)
            if (viewModel.updateCheckEnabled) {
                HorizontalDivider()
                Text(stringResource(R.string.app_updates), style = MaterialTheme.typography.titleMedium)
                when (val state = updateState) {
                    UpdateUiState.Idle -> OutlinedButton(viewModel::checkForUpdate) { Text(stringResource(R.string.check_updates)) }
                    UpdateUiState.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.checking_updates))
                    }
                    UpdateUiState.UpToDate -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.up_to_date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(viewModel::checkForUpdate) { Text(stringResource(R.string.check_updates)) }
                    }
                    is UpdateUiState.Available -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.update_available, state.release.tag))
                        FilledTonalButton(viewModel::downloadUpdate) { Text(stringResource(R.string.download_update)) }
                    }
                    is UpdateUiState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.downloading_update, state.progress))
                        LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateUiState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.update_ready, state.release.tag))
                        if (state.permissionRequired) Text(
                            stringResource(R.string.allow_install_updates),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(viewModel::installUpdate) { Text(stringResource(R.string.install_update)) }
                    }
                    is UpdateUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(viewModel::checkForUpdate) { Text(stringResource(R.string.try_again)) }
                    }
                }
            }
            Text(stringResource(R.string.license_summary), style = MaterialTheme.typography.bodySmall)
        }
    }
}
