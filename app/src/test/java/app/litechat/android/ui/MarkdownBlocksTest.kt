package app.litechat.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {
    @Test
    fun splitsParagraphsOnBlankLines() {
        assertEquals(listOf("First para", "Second para"), splitMarkdownBlocks("First para\n\nSecond para"))
    }

    @Test
    fun keepsFencedCodeBlockIntactAcrossBlankLines() {
        val code = "```kotlin\nval a = 1\n\nval b = 2\n```"
        assertEquals(listOf(code), splitMarkdownBlocks(code))
    }

    @Test
    fun keepsTableRowsInOneBlock() {
        val table = "| a | b |\n| - | - |\n| 1 | 2 |"
        assertEquals(listOf("Intro", table), splitMarkdownBlocks("Intro\n\n$table"))
    }

    @Test
    fun treatsUnterminatedFenceAsSingleTrailingBlock() {
        val streaming = "Intro line\n\n```python\nprint(1)\n\nprint(2)"
        assertEquals(listOf("Intro line", "```python\nprint(1)\n\nprint(2)"), splitMarkdownBlocks(streaming))
    }

    @Test
    fun blankOrEmptyContentYieldsNoBlocks() {
        assertTrue(splitMarkdownBlocks("").isEmpty())
        assertTrue(splitMarkdownBlocks("   \n\n  ").isEmpty())
    }

    @Test
    fun completedBlocksAreStableAsTheReplyGrows() {
        // The whole point of block rendering: once a later block exists, every earlier block's text
        // must be byte-identical across the stream so the UI can seal it and stop repainting it.
        val stream = listOf(
            "Para one",
            "Para one\n",
            "Para one\n\n",
            "Para one\n\nPara two starts",
            "Para one\n\nPara two starts here\n\n```js\nconst x = 1",
            "Para one\n\nPara two starts here\n\n```js\nconst x = 1\n```\n\nDone"
        )
        var previous = splitMarkdownBlocks(stream.first())
        for (snapshot in stream.drop(1)) {
            val current = splitMarkdownBlocks(snapshot)
            // Every block except the last one of the previous snapshot must be unchanged.
            val sealedCount = (previous.size - 1).coerceAtLeast(0)
            assertEquals(
                "sealed blocks changed for snapshot: $snapshot",
                previous.take(sealedCount),
                current.take(sealedCount)
            )
            previous = current
        }
        assertEquals(
            listOf("Para one", "Para two starts here", "```js\nconst x = 1\n```", "Done"),
            previous
        )
    }
}
