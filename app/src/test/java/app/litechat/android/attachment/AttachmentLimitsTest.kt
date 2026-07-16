package app.litechat.android.attachment

import org.junit.Assert.*
import org.junit.Test

class AttachmentLimitsTest {
    @Test fun truncationIsExplicitAndExact() {
        val (text, truncated) = AttachmentProcessor.truncateText("x".repeat(100_001))
        assertEquals(100_000, text.length)
        assertTrue(truncated)
        assertFalse(AttachmentProcessor.truncateText("short").second)
    }
}
