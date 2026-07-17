package app.litechat.android.ui

import app.litechat.android.data.model.ConversationEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ConversationGroupingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 7, 17, 12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun pinnedComesFirstAndGroupsUseLocalDateBoundaries() {
        fun time(daysAgo: Long, hour: Int = 10) = LocalDateTime.of(2026, 7, 17, hour, 0)
            .minusDays(daysAgo).atZone(zone).toInstant().toEpochMilli()
        val values = listOf(
            ConversationEntity("older", "Older", updatedAt = time(7)),
            ConversationEntity("today", "Today", updatedAt = time(0)),
            ConversationEntity("week", "Week", updatedAt = time(6)),
            ConversationEntity("pinned", "Pinned", updatedAt = time(20), pinnedAt = now)
        )

        val sections = groupConversations(values, now, zone)

        assertEquals(
            listOf(ConversationGroup.PINNED, ConversationGroup.TODAY, ConversationGroup.LAST_SEVEN_DAYS, ConversationGroup.OLDER),
            sections.map { it.group }
        )
        assertEquals("pinned", sections.first().conversations.single().id)
    }
}
