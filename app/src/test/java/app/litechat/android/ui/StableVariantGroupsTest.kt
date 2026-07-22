package app.litechat.android.ui

import app.litechat.android.data.model.ResponseVariantEntity
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class StableVariantGroupsTest {
    @Test fun reusesUnchangedGroupsAndReplacesOnlyUpdatedMessage() {
        val first = ResponseVariantEntity("v1", "m1", "old", "p", "model", updatedAt = 1L)
        val second = ResponseVariantEntity("v2", "m2", "settled", "p", "model", updatedAt = 1L)
        val previous = stableVariantGroups(listOf(first, second), emptyMap())

        val current = stableVariantGroups(
            listOf(first.copy(content = "new", updatedAt = 2L), second.copy()),
            previous
        )

        assertNotSame(previous.getValue("m1"), current.getValue("m1"))
        assertSame(previous.getValue("m2"), current.getValue("m2"))
    }

    @Test fun statusChangeIsNotHiddenWhenTimestampIsUnchanged() {
        val streaming = ResponseVariantEntity("v", "m", "", "p", "model", updatedAt = 1L)
        val previous = stableVariantGroups(listOf(streaming), emptyMap())

        val current = stableVariantGroups(
            listOf(streaming.copy(status = app.litechat.android.data.model.MessageStatus.COMPLETE)),
            previous
        )

        assertNotSame(previous.getValue("m"), current.getValue("m"))
    }
}
