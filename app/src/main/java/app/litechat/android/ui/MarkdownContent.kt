package app.litechat.android.ui

import android.os.SystemClock
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val lastRender = remember { longArrayOf(0L) }
    AndroidView(
        factory = { context ->
            AppCompatTextView(context).apply {
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.15f)
                includeFontPadding = false
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            // Normalizing math and parsing Markdown (then typesetting LaTeX) is the streaming hot
            // path. While tokens are still arriving, cap how often the rich layout is rebuilt; the
            // terminal, non-streaming pass always renders so the finished message is never stale.
            // Raw content is the change key, so normalization runs only on the ticks we render.
            val now = SystemClock.elapsedRealtime()
            val due = !streaming || now - lastRender[0] >= STREAM_RENDER_MIN_INTERVAL_MS
            if (view.tag != content && due) {
                view.tag = content
                lastRender[0] = now
                renderer.setMarkdown(view, normalizeMarkdownMath(content))
            }
        },
        modifier = modifier
    )
}

private const val STREAM_RENDER_MIN_INTERVAL_MS = 200L

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
