package app.litechat.android.ui

import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import app.litechat.android.R
import kotlinx.coroutines.launch

internal val LocalAppSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

/**
 * True when the user has asked the system to remove animations (Developer options /
 * accessibility "Remove animations" set the global animator duration scale to 0). Motion that is
 * purely decorative — infinite loops, theme cross-fades — should fall back to a static state so the
 * app stays comfortable and battery-light for those users.
 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

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
