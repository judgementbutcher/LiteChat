package app.litechat.android.ui

import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlinx.coroutines.Dispatchers
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

// Markwon has no equals(); wrapping it lets Compose treat the renderer as a stable parameter so a
// sealed block whose text is unchanged is skipped entirely (never recomposed) while streaming.
@Immutable
private class StableRenderer(val markwon: Markwon)

// A whole-message render (source text + its parsed spans), prepared off the main thread so the
// settled reply can be shown in a single selectable TextView without re-parsing on the UI thread.
private class SettledDoc(val source: String, val spanned: Spanned)

@Composable
internal fun MarkdownContent(content: String, renderer: Markwon, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    // The reply text sits directly on the canvas, so paint each TextView with an opaque canvas-
    // coloured background: it lets a view cover its previous frame when its layout is swapped,
    // instead of leaving the old glyphs ghosting underneath.
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    // The accent is monochrome in the ChatGPT-style palette, so links get their own blue that reads
    // on both the light and dark canvas instead of borrowing the near-black/near-white primary.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val linkColor = (if (dark) Color(0xFF7EB3FF) else Color(0xFF1A73E8)).toArgb()
    val stableRenderer = remember(renderer) { StableRenderer(renderer) }

    // Once a reply has streamed we keep it on the block path until its settled render is ready, so
    // the hand-off never blanks. Messages that were never streamed here (chat history) skip straight
    // to the settled view and look exactly as before.
    var everStreamed by remember(stableRenderer) { mutableStateOf(streaming) }
    LaunchedEffect(streaming) { if (streaming) everStreamed = true }

    // Prepare the whole-message render off the main thread whenever the reply is settled.
    var settled by remember(stableRenderer) { mutableStateOf<SettledDoc?>(null) }
    LaunchedEffect(content, streaming, stableRenderer) {
        if (streaming) {
            settled = null
            return@LaunchedEffect
        }
        val spanned = withContext(Dispatchers.Default) {
            stableRenderer.markwon.render(stableRenderer.markwon.parse(normalizeMarkdownMath(content)))
        }
        settled = SettledDoc(content, spanned)
    }
    val settledSpanned = settled?.takeIf { it.source == content }?.spanned

    // While streaming — or while a just-finished reply's settled render is still being prepared —
    // render one Markdown block at a time. Completed ("sealed") blocks keep a stable slot and a
    // byte-identical string, so Compose skips them and their TextView is never re-set; only the
    // final, still-growing block re-parses and repaints on each token. That confines every streaming
    // redraw to the tail block instead of swapping the whole reply's layout, which is what made the
    // entire answer flicker. It is how the ChatGPT app streams without flashing: text already on
    // screen never moves, only the new tail is painted.
    if (streaming || (everStreamed && settledSpanned == null)) {
        StreamingBlocks(content, stableRenderer, textColor, linkColor, backgroundColor, modifier)
    } else {
        SettledText(settledSpanned, stableRenderer, textColor, linkColor, backgroundColor, modifier)
    }
}

@Composable
private fun StreamingBlocks(
    content: String,
    renderer: StableRenderer,
    textColor: Int,
    linkColor: Int,
    backgroundColor: Int,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { splitMarkdownBlocks(content) }
    val blockModifier = remember { Modifier.fillMaxWidth() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { index, block ->
            key(index) {
                MarkdownBlock(block, renderer, textColor, linkColor, backgroundColor, blockModifier)
            }
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: String,
    renderer: StableRenderer,
    textColor: Int,
    linkColor: Int,
    backgroundColor: Int,
    modifier: Modifier = Modifier
) {
    var view by remember { mutableStateOf<AppCompatTextView?>(null) }
    // Last colours pushed to the view. Re-setting a colour invalidates the TextView, so we only
    // touch it on a real theme change and never on the per-token recompositions of sealed blocks.
    val applied = remember { intArrayOf(textColor, linkColor, backgroundColor) }
    // Parse off the main thread, then swap the layout in place. The previous frame stays visible
    // until the new one is ready, so the block grows without ever blanking. Blocks stay
    // non-selectable: a selectable TextView rebuilds its Editor on every setText, which is the
    // per-token repaint we are avoiding (selection returns with the settled view below).
    LaunchedEffect(view, block, renderer) {
        val target = view ?: return@LaunchedEffect
        val spanned = withContext(Dispatchers.Default) {
            renderer.markwon.render(renderer.markwon.parse(normalizeMarkdownMath(block)))
        }
        renderer.markwon.setParsedMarkdown(target, spanned)
    }
    AndroidView(
        factory = { context ->
            AppCompatTextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.15f)
                includeFontPadding = false
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setBackgroundColor(backgroundColor)
                movementMethod = LinkMovementMethod.getInstance()
            }.also { view = it }
        },
        update = { target ->
            if (applied[0] != textColor) { target.setTextColor(textColor); applied[0] = textColor }
            if (applied[1] != linkColor) { target.setLinkTextColor(linkColor); applied[1] = linkColor }
            if (applied[2] != backgroundColor) { target.setBackgroundColor(backgroundColor); applied[2] = backgroundColor }
        },
        modifier = modifier
    )
}

@Composable
private fun SettledText(
    spanned: Spanned?,
    renderer: StableRenderer,
    textColor: Int,
    linkColor: Int,
    backgroundColor: Int,
    modifier: Modifier = Modifier
) {
    val applied = remember { intArrayOf(textColor, linkColor, backgroundColor) }
    // Last spans applied. TextView may copy what we set, so we can't compare against target.text;
    // tracking the source Spanned keeps us from re-applying (and re-invalidating) on recomposition.
    val appliedSpanned = remember { arrayOfNulls<Spanned>(1) }
    AndroidView(
        factory = { context ->
            AppCompatTextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.15f)
                includeFontPadding = false
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setBackgroundColor(backgroundColor)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { target ->
            if (applied[0] != textColor) { target.setTextColor(textColor); applied[0] = textColor }
            if (applied[1] != linkColor) { target.setLinkTextColor(linkColor); applied[1] = linkColor }
            if (applied[2] != backgroundColor) { target.setBackgroundColor(backgroundColor); applied[2] = backgroundColor }
            // The spans are already prepared off the main thread, so applying them here (during the
            // layout pass) shows the settled reply in the same frame the blocks are replaced — no
            // blank at the hand-off. Selection is enabled now that the text has stopped changing.
            if (spanned != null && spanned !== appliedSpanned[0]) {
                renderer.markwon.setParsedMarkdown(target, spanned)
                appliedSpanned[0] = spanned
                if (!target.isTextSelectable) {
                    target.setTextIsSelectable(true)
                    target.movementMethod = LinkMovementMethod.getInstance()
                }
            }
        },
        modifier = modifier
    )
}

// Split the reply into top-level Markdown blocks on blank lines, keeping fenced code blocks (which
// may contain blank lines) intact. Splitting left-to-right makes earlier blocks "sticky": once a
// later block exists, a completed block's text never changes again, which is what lets the UI seal
// it and stop repainting it. An unterminated fence stays a single trailing block while it streams.
internal fun splitMarkdownBlocks(content: String): List<String> {
    if (content.isBlank()) return emptyList()
    val blocks = mutableListOf<String>()
    val current = StringBuilder()
    var fence: String? = null
    for (line in content.split("\n")) {
        val trimmed = line.trimStart()
        val marker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (marker != null) {
            if (fence == null) fence = marker
            else if (trimmed.startsWith(fence)) fence = null
        }
        if (line.isBlank() && fence == null) {
            if (current.isNotEmpty()) {
                blocks.add(current.toString())
                current.setLength(0)
            }
        } else {
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
    }
    if (current.isNotEmpty()) blocks.add(current.toString())
    return blocks
}

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
