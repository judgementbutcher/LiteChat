package app.litechat.android.ui

import app.litechat.android.data.model.AttachmentEntity
import app.litechat.android.data.model.ConversationEntity
import app.litechat.android.data.model.MessageEntity
import app.litechat.android.data.model.ResponseVariantEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ConversationGroup { PINNED, TODAY, LAST_SEVEN_DAYS, OLDER }

data class ConversationSection(
    val group: ConversationGroup,
    val conversations: List<ConversationEntity>
)

data class ConversationListUiState(
    val query: String = "",
    val sections: List<ConversationSection> = emptyList(),
    val archived: Boolean = false
)

data class ChatUiState(
    val conversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val variantsByMessage: Map<String, List<ResponseVariantEntity>> = emptyMap(),
    val attachmentsByMessage: Map<String, List<AttachmentEntity>> = emptyMap()
)

fun groupConversations(
    conversations: List<ConversationEntity>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<ConversationSection> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val grouped = conversations.sortedWith(
        compareByDescending<ConversationEntity> { it.pinnedAt != null }
            .thenByDescending { it.pinnedAt ?: it.updatedAt }
            .thenByDescending { it.updatedAt }
    ).groupBy { conversation ->
        if (conversation.pinnedAt != null) return@groupBy ConversationGroup.PINNED
        val date = Instant.ofEpochMilli(conversation.updatedAt).atZone(zoneId).toLocalDate()
        when {
            date == today -> ConversationGroup.TODAY
            !date.isBefore(today.minusDays(6)) -> ConversationGroup.LAST_SEVEN_DAYS
            else -> ConversationGroup.OLDER
        }
    }
    return ConversationGroup.entries.mapNotNull { group ->
        grouped[group]?.takeIf(List<ConversationEntity>::isNotEmpty)?.let { ConversationSection(group, it) }
    }
}

fun relativeConversationTime(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): RelativeConversationTime {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    return when (val days = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()) {
        0 -> RelativeConversationTime.TODAY
        1 -> RelativeConversationTime.YESTERDAY
        in 2..6 -> RelativeConversationTime.THIS_WEEK
        else -> RelativeConversationTime.OLDER
    }
}

enum class RelativeConversationTime { TODAY, YESTERDAY, THIS_WEEK, OLDER }
