package app.litechat.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.litechat.android.data.model.*

@Database(
    entities = [
        ProviderConfigEntity::class,
        ModelConfigEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ResponseVariantEntity::class,
        AttachmentEntity::class,
        PromptTemplateEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LiteChatDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun variantDao(): VariantDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun promptTemplateDao(): PromptTemplateDao

    companion object {
        fun create(context: Context): LiteChatDatabase = Room.databaseBuilder(
            context.applicationContext,
            LiteChatDatabase::class.java,
            "litechat.db"
        ).build()
    }
}
