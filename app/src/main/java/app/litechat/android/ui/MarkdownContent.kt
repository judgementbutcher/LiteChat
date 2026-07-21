package app.litechat.android.ui

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

@Composable
internal fun rememberMarkdownRenderer(): Markwon {
    val context = LocalContext.current
    val density = LocalDensity.current
    val contentColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val inlineSize = with(density) { 17.sp.toPx() }
    val blockSize = with(density) { 20.sp.toPx() }
    return remember(context, contentColor, linkColor, inlineSize, blockSize) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(
                JLatexMathPlugin.create(inlineSize, blockSize) { builder ->
                    builder.inlinesEnabled(true)
                    builder.theme()
                        .textColor(contentColor)
                        .blockFitCanvas(true)
                        .padding(JLatexMathTheme.Padding.symmetric(8, 4))
                }
            )
            .build()
    }
}

@Composable
internal fun MarkdownContent(content: String, renderer: Markwon, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    // The reply text sits directly on the canvas, so paint the TextView with an opaque canvas-
    // coloured background. Without it a plain (non-selectable) TextView does not clear its own
    // pixels when setMarkdown replaces the layout mid-stream: the previous frame's glyphs stay
    // underneath the new ones and the reply looks like two answers stacked on top of each other.
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    // The accent is monochrome in the ChatGPT-style palette, so links get their own blue that reads
    // on both the light and dark canvas instead of borrowing the near-black/near-white primary.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val linkColor = (if (dark) Color(0xFF7EB3FF) else Color(0xFF1A73E8)).toArgb()
    var textView by remember { mutableStateOf<AppCompatTextView?>(null) }
    val request = MarkdownRenderRequest(content, streaming)
    val latestRequest by rememberUpdatedState(request)
    val renderRequests = remember(renderer) { Channel<MarkdownRenderRequest>(Channel.CONFLATED) }
    DisposableEffect(renderRequests) {
        onDispose { renderRequests.close() }
    }
    LaunchedEffect(request, renderRequests) {
        renderRequests.trySend(request)
    }
    LaunchedEffect(textView, renderer, renderRequests) {
        val view = textView ?: return@LaunchedEffect
        for (pending in renderRequests) {
            val rendered = withContext(Dispatchers.Default) {
                renderer.render(renderer.parse(normalizeMarkdownMath(pending.content)))
            }
            if (pending != latestRequest) continue
            renderer.setParsedMarkdown(view, rendered)
            view.tag = pending
            if (!pending.streaming && !view.isTextSelectable) {
                view.setTextIsSelectable(true)
                view.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }
    AndroidView(
        factory = { context ->
            AppCompatTextView(context).apply {
                // Selection is enabled only once the stream settles (see below). A selectable
                // TextView rebuilds its whole text Editor on every setText, which is what makes a
                // streaming reply blank-and-repaint on every token. ChatGPT keeps in-flight text
                // non-selectable for the same reason.
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.15f)
                includeFontPadding = false
                movementMethod = LinkMovementMethod.getInstance()
            }.also { textView = it }
        },
        update = { view ->
            view.setBackgroundColor(backgroundColor)
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            if (streaming && view.isTextSelectable) {
                view.setTextIsSelectable(false)
                view.movementMethod = LinkMovementMethod.getInstance()
            }
        },
        modifier = modifier
    )
}

private data class MarkdownRenderRequest(val content: String, val streaming: Boolean)

internal fun normalizeMarkdownMath(value: String): String = value
    .split("```")
    .mapIndexed { index, segment -> if (index % 2 == 0) normalizeMathSegment(stripRemoteImages(segment)) else segment }
    .joinToString("```")

private fun normalizeMathSegment(value: String): String {
    val blocks = Regex("""\\\[([\s\S]*?)\\]""").replace(value) { match ->
        "\n\$\$\n${match.groupValues[1].trim()}\n\$\$\n"
    }
    val parentheses = Regex("""\\\(([\s\S]*?)\\\)""").replace(blocks) { match ->
        "\$\$${match.groupValues[1].trim()}\$\$"
    }
    return normalizeSingleDollarMath(parentheses)
}

private fun normalizeSingleDollarMath(value: String): String {
    val result = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val isSingleDollar = value[index] == '$' &&
            (index == 0 || value[index - 1] != '$' && value[index - 1] != '\\') &&
            (index == value.lastIndex || value[index + 1] != '$')
        if (!isSingleDollar) {
            result.append(value[index++])
            continue
        }
        var end = index + 1
        while (end < value.length) {
            if (value[end] == '$' && value[end - 1] != '\\' &&
                (end == value.lastIndex || value[end + 1] != '$')) break
            end++
        }
        if (end >= value.length || value.substring(index + 1, end).isBlank()) {
            result.append(value[index++])
            continue
        }
        result.append("\$\$").append(value, index + 1, end).append("\$\$")
        index = end + 1
    }
    return result.toString()
}

private fun stripRemoteImages(value: String): String =
    value.replace(Regex("!\\[([^]]*)]\\(https?://[^)]+\\)"), "[$1]")
