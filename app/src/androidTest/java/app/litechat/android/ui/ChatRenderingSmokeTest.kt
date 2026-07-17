package app.litechat.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.litechat.android.LiteChatApplication
import app.litechat.android.MainActivity
import app.litechat.android.data.model.ConversationEntity
import app.litechat.android.data.model.MessageEntity
import app.litechat.android.data.model.MessageStatus
import app.litechat.android.data.model.ResponseVariantEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatRenderingSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun removeFixture() {
        runBlocking {
            val database = (compose.activity.application as LiteChatApplication).container.database
            database.conversationDao().get("rendering-smoke")?.let { database.conversationDao().delete(it) }
        }
    }

    @Test fun streamingSettlesIntoMarkdownAndLatexWithoutLosingTheConversation() {
        runBlocking {
            val database = (compose.activity.application as LiteChatApplication).container.database
            val conversationId = "rendering-smoke"
            val assistantId = "rendering-assistant"
            val variantId = "rendering-variant"
            val conversation = ConversationEntity(
                conversationId,
                "LaTeX rendering",
                providerId = "openai",
                modelId = "gpt-4.1-mini"
            )
            database.conversationDao().upsert(conversation)
            database.messageDao().upsertAll(listOf(
                MessageEntity("rendering-user", conversationId, "user", "Prove the Lagrange mean value theorem.", createdAt = 1L, updatedAt = 1L),
                MessageEntity(assistantId, conversationId, "assistant", "", selectedVariantId = variantId, createdAt = 2L, updatedAt = 2L)
            ))
            val markdown = """
                    ## Lagrange mean value theorem

                    If ${'$'}f${'$'} is continuous on ${'$'}[a,b]${'$'} and differentiable on ${'$'}(a,b)${'$'}, then there is ${'$'}c\in(a,b)${'$'} such that

                    \[
                    f'(c)=\frac{f(b)-f(a)}{b-a}.
                    \]

                    Apply Rolle's theorem to ${'$'}g(x)=f(x)-\frac{f(b)-f(a)}{b-a}(x-a)${'$'}. Then ${'$'}g(a)=g(b)${'$'}, so ${'$'}g'(c)=0${'$'}, which gives the result.

                    \[
                    \begin{pmatrix} a & b \\ c & d \end{pmatrix}
                    \]
            """.trimIndent()
            val streaming = ResponseVariantEntity(
                id = variantId,
                messageId = assistantId,
                providerId = "openai",
                modelId = "gpt-4.1-mini"
            )
            database.variantDao().upsert(streaming)

            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("LaTeX rendering").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("LaTeX rendering").performClick()

            markdown.indices.filter { it % 24 == 0 }.forEach { end ->
                database.variantDao().upsert(streaming.copy(content = markdown.take(end), updatedAt = end.toLong()))
                delay(8)
            }
            database.variantDao().upsert(
                streaming.copy(
                    content = markdown,
                    status = MessageStatus.COMPLETE,
                    updatedAt = System.currentTimeMillis()
                )
            )
            compose.waitForIdle()
            delay(1_000)

            compose.onNodeWithText("Prove the Lagrange mean value theorem.").assertIsDisplayed()
            onView(withText(containsString("Lagrange mean value theorem"))).check(matches(isDisplayed()))
        }
    }
}
