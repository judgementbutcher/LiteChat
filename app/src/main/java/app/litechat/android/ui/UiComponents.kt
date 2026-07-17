package app.litechat.android.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import app.litechat.android.R
import kotlinx.coroutines.launch

internal val LocalAppSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

@Composable
internal fun rememberCopyAction(content: String): () -> Unit {
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalAppSnackbarHostState.current
    val copied = stringResource(R.string.copy_success)
    val scope = rememberCoroutineScope()
    return remember(content, copied, snackbar) {
        {
            clipboard.setText(AnnotatedString(content))
            scope.launch { snackbar?.showSnackbar(copied) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccessibleIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(48.dp).semantics { contentDescription = label }
        ) { Icon(icon, contentDescription = null) }
    }
}
