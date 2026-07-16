package app.litechat.android.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.litechat.android.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createsConversationWithoutAKey() {
        compose.onAllNodesWithText("LiteChat").onFirst().assertExists()
        compose.onAllNodesWithText("New chat").onLast().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Start a private, local-first conversation.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start a private, local-first conversation.").assertExists()
    }
}
