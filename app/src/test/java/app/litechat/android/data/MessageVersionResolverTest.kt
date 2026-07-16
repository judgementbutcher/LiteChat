package app.litechat.android.data

import app.litechat.android.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageVersionResolverTest {
    @Test fun selectedIdWinsAndMissingIdFallsBackToNewest() {
        val message = MessageEntity("m", "c", "assistant", "", selectedVariantId = "v1")
        val variants = listOf(
            ResponseVariantEntity("v1", "m", "first", "p", "model"),
            ResponseVariantEntity("v2", "m", "second", "p", "model")
        )
        assertEquals("first", MessageVersionResolver.selected(message, variants)?.content)
        assertEquals("second", MessageVersionResolver.selected(message.copy(selectedVariantId = "gone"), variants)?.content)
    }
}
