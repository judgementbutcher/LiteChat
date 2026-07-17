package app.litechat.android.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.litechat.android.data.local.LiteChatDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiteChatDatabase::class.java
    )

    @Test fun migrateOneToTwoPreservesConversationAndDefaultsPinnedAtToNull() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO conversations (id, title, systemPrompt, providerId, modelId, searchEnabled, createdAt, updatedAt, archived) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("c", "Legacy", "", null, null, 0, 1L, 2L, 0)
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            "migration-test",
            2,
            true,
            LiteChatDatabase.MIGRATION_1_2
        )
        migrated.query("SELECT title, pinnedAt FROM conversations WHERE id = 'c'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Legacy", cursor.getString(0))
            assertEquals(true, cursor.isNull(1))
        }
        migrated.close()
    }
}
