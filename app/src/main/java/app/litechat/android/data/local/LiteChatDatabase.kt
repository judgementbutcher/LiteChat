package app.litechat.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinnedAt INTEGER")
            }
        }

        fun create(context: Context): LiteChatDatabase = Room.databaseBuilder(
            context.applicationContext,
            LiteChatDatabase::class.java,
            "litechat.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
