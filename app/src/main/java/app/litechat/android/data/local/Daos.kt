package app.litechat.android.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.litechat.android.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt") fun observeAll(): Flow<List<ProviderConfigEntity>>
    @Query("SELECT * FROM providers ORDER BY createdAt") suspend fun getAll(): List<ProviderConfigEntity>
    @Query("SELECT * FROM providers WHERE id = :id") suspend fun get(id: String): ProviderConfigEntity?
    @Query("SELECT COUNT(*) FROM providers") suspend fun count(): Int
    @Upsert suspend fun upsert(value: ProviderConfigEntity)
    @Upsert suspend fun upsertAll(values: List<ProviderConfigEntity>)
    @Delete suspend fun delete(value: ProviderConfigEntity)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE enabled = 1 ORDER BY providerId, displayName") fun observeEnabled(): Flow<List<ModelConfigEntity>>
    @Query("SELECT * FROM models ORDER BY providerId, displayName") fun observeAll(): Flow<List<ModelConfigEntity>>
    @Query("SELECT * FROM models ORDER BY providerId, displayName") suspend fun getAll(): List<ModelConfigEntity>
    @Query("SELECT * FROM models WHERE providerId = :providerId AND modelId = :modelId") suspend fun get(providerId: String, modelId: String): ModelConfigEntity?
    @Upsert suspend fun upsert(value: ModelConfigEntity)
    @Upsert suspend fun upsertAll(values: List<ModelConfigEntity>)
    @Query("DELETE FROM models WHERE providerId = :providerId AND modelId = :modelId") suspend fun delete(providerId: String, modelId: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY CASE WHEN pinnedAt IS NULL THEN 1 ELSE 0 END, pinnedAt DESC, updatedAt DESC") fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT DISTINCT c.* FROM conversations c LEFT JOIN messages m ON m.conversationId = c.id WHERE c.archived = 0 AND (:query = '' OR c.title LIKE '%' || :query || '%' OR m.content LIKE '%' || :query || '%') ORDER BY CASE WHEN c.pinnedAt IS NULL THEN 1 ELSE 0 END, c.pinnedAt DESC, c.updatedAt DESC") fun observeSearch(query: String): Flow<List<ConversationEntity>>
    @Query("SELECT DISTINCT c.* FROM conversations c LEFT JOIN messages m ON m.conversationId = c.id WHERE c.archived = 1 AND (:query = '' OR c.title LIKE '%' || :query || '%' OR m.content LIKE '%' || :query || '%') ORDER BY c.updatedAt DESC") fun observeArchived(query: String): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") suspend fun getAll(): List<ConversationEntity>
    @Query("SELECT * FROM conversations WHERE id = :id") fun observe(id: String): Flow<ConversationEntity?>
    @Query("SELECT * FROM conversations WHERE id = :id") suspend fun get(id: String): ConversationEntity?
    @Upsert suspend fun upsert(value: ConversationEntity)
    @Upsert suspend fun upsertAll(values: List<ConversationEntity>)
    @Delete suspend fun delete(value: ConversationEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id") fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id") suspend fun getForConversation(conversationId: String): List<MessageEntity>
    @Query("SELECT * FROM messages ORDER BY createdAt, id") suspend fun getAll(): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE id = :id") suspend fun get(id: String): MessageEntity?
    @Upsert suspend fun upsert(value: MessageEntity)
    @Upsert suspend fun upsertAll(values: List<MessageEntity>)
    @Update suspend fun update(value: MessageEntity)
    @Query("DELETE FROM messages WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface VariantDao {
    @Query("SELECT v.* FROM response_variants v INNER JOIN messages m ON m.id = v.messageId WHERE m.conversationId = :conversationId ORDER BY v.createdAt") fun observeForConversation(conversationId: String): Flow<List<ResponseVariantEntity>>
    @Query("SELECT v.* FROM response_variants v INNER JOIN messages m ON m.id = v.messageId WHERE m.conversationId = :conversationId ORDER BY v.createdAt") suspend fun getForConversation(conversationId: String): List<ResponseVariantEntity>
    @Query("SELECT * FROM response_variants ORDER BY createdAt") suspend fun getAll(): List<ResponseVariantEntity>
    @Query("SELECT * FROM response_variants WHERE id = :id") suspend fun get(id: String): ResponseVariantEntity?
    @Upsert suspend fun upsert(value: ResponseVariantEntity)
    @Upsert suspend fun upsertAll(values: List<ResponseVariantEntity>)
}

@Dao
interface AttachmentDao {
    @Query("SELECT a.* FROM attachments a INNER JOIN messages m ON m.id = a.messageId WHERE m.conversationId = :conversationId ORDER BY a.createdAt") fun observeForConversation(conversationId: String): Flow<List<AttachmentEntity>>
    @Query("SELECT a.* FROM attachments a INNER JOIN messages m ON m.id = a.messageId WHERE m.conversationId = :conversationId ORDER BY a.createdAt") suspend fun getForConversation(conversationId: String): List<AttachmentEntity>
    @Query("SELECT * FROM attachments ORDER BY createdAt") suspend fun getAll(): List<AttachmentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(values: List<AttachmentEntity>)
}

@Dao
interface PromptTemplateDao {
    @Query("SELECT * FROM prompt_templates ORDER BY updatedAt DESC") fun observeAll(): Flow<List<PromptTemplateEntity>>
    @Query("SELECT * FROM prompt_templates ORDER BY updatedAt DESC") suspend fun getAll(): List<PromptTemplateEntity>
    @Upsert suspend fun upsert(value: PromptTemplateEntity)
    @Upsert suspend fun upsertAll(values: List<PromptTemplateEntity>)
    @Delete suspend fun delete(value: PromptTemplateEntity)
}
