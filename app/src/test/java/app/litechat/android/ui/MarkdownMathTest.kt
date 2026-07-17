package app.litechat.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMathTest {
    @Test
    fun normalizesCommonInlineAndBlockDelimiters() {
        val input = """
            Inline \(a^2+b^2=c^2\) and ${'$'}e^{i\pi}+1=0${'$'}.

            \[
            \frac{d}{dx} x^2 = 2x
            \]
        """.trimIndent()

        val result = normalizeMarkdownMath(input)

        assertTrue(result.contains("${'$'}${'$'}a^2+b^2=c^2${'$'}${'$'}"))
        assertTrue(result.contains("${'$'}${'$'}e^{i\\pi}+1=0${'$'}${'$'}"))
        assertTrue(result.contains("${'$'}${'$'}\n\\frac{d}{dx} x^2 = 2x\n${'$'}${'$'}"))
    }

    @Test
    fun leavesCodeAndUnmatchedCurrencyUntouched() {
        val input = "Price ${'$'}5\n```kotlin\nval formula = \"${'$'}x${'$'}\"\n```"

        assertEquals(input, normalizeMarkdownMath(input))
    }

    @Test
    fun removesRemoteImagesWithoutDroppingAltText() {
        assertEquals("[diagram]", normalizeMarkdownMath("![diagram](https://example.com/a.png)"))
    }
}
