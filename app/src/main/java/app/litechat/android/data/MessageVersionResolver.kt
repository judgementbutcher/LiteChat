package app.litechat.android.data

import app.litechat.android.data.model.MessageEntity
import app.litechat.android.data.model.ResponseVariantEntity

object MessageVersionResolver {
    fun selected(message: MessageEntity, variants: List<ResponseVariantEntity>): ResponseVariantEntity? =
        message.selectedVariantId?.let { id -> variants.firstOrNull { it.id == id && it.messageId == message.id } }
            ?: variants.lastOrNull { it.messageId == message.id }
}
