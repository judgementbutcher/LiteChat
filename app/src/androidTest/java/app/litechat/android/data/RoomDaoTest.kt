package app.litechat.android.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.litechat.android.data.local.LiteChatDatabase
import app.litechat.android.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDaoTest {
    private lateinit var db: LiteChatDatabase

    @Before fun createDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiteChatDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun closeDb() = db.close()

    @Test fun conversationCascadeAndVariantSelectionPersist() = runBlocking {
        db.conversationDao().upsert(ConversationEntity("c", "Title"))
        db.messageDao().upsert(MessageEntity("m", "c", "assistant", "", "v"))
        db.variantDao().upsert(ResponseVariantEntity("v", "m", "hello", "p", "model", MessageStatus.COMPLETE))
        assertEquals("v", db.messageDao().observeForConversation("c").first().single().selectedVariantId)
        db.conversationDao().delete(requireNotNull(db.conversationDao().get("c")))
        assertEquals(0, db.messageDao().getForConversation("c").size)
    }
}
