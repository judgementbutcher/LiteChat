package app.litechat.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseFrameParserTest {
    @Test fun parsesEventAndMultilineData() {
        val parser = SseFrameParser()
        assertNull(parser.accept("event: response.output_text.delta"))
        assertNull(parser.accept("data: {\"delta\":"))
        assertNull(parser.accept("data: \"hello\"}"))
        assertEquals("response.output_text.delta" to "{\"delta\":\n\"hello\"}", parser.accept(""))
    }

    @Test fun flushesFinalFrameWithoutBlankLine() {
        val parser = SseFrameParser()
        parser.accept("data: [DONE]")
        assertEquals(null to "[DONE]", parser.finish())
    }
}
