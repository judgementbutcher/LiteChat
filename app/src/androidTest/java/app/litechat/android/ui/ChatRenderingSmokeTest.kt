package app.litechat.android.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import app.litechat.android.data.model.AttachmentEntity
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatRenderingSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun removeFixture() {
        runBlocking {
            val database = (compose.activity.application as LiteChatApplication).container.database
            database.conversationDao().get("rendering-smoke")?.let { database.conversationDao().delete(it) }
            database.conversationDao().get("attachment-smoke")?.let { database.conversationDao().delete(it) }
            File(compose.activity.filesDir, "attachment-smoke.png").delete()
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

            val streamingPreviewEnd = markdown.indexOf("Apply Rolle's theorem")
            markdown.indices.filter { it % 24 == 0 && it <= streamingPreviewEnd }.forEach { end ->
                database.variantDao().upsert(streaming.copy(content = markdown.take(end), updatedAt = end.toLong()))
                delay(8)
            }
            database.variantDao().upsert(streaming.copy(content = markdown.take(streamingPreviewEnd), updatedAt = streamingPreviewEnd.toLong()))
            compose.waitForIdle()
            onView(withText(containsString("Lagrange mean value theorem"))).check(matches(isDisplayed()))

            val longMarkdown = markdown + "\n\n" + (1..24).joinToString("\n\n") {
                "Streaming paragraph $it keeps the response tall while output is still arriving."
            }
            database.variantDao().upsert(streaming.copy(content = longMarkdown, updatedAt = System.currentTimeMillis()))
            compose.waitForIdle()
            compose.onNodeWithTag("chat-bottom-anchor").assertIsDisplayed()

            database.variantDao().upsert(
                streaming.copy(
                    content = longMarkdown,
                    status = MessageStatus.COMPLETE,
                    updatedAt = System.currentTimeMillis()
                )
            )
            compose.waitForIdle()
            delay(1_000)

            onView(withText(containsString("Lagrange mean value theorem"))).check(matches(isDisplayed()))
        }
    }

    @Test fun sentImageOpensAFullScreenPreview() {
        runBlocking {
            val database = (compose.activity.application as LiteChatApplication).container.database
            val file = File(compose.activity.filesDir, "attachment-smoke.png")
            Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.rgb(40, 120, 210))
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            database.conversationDao().upsert(ConversationEntity("attachment-smoke", "Image preview"))
            database.messageDao().upsert(MessageEntity("attachment-user", "attachment-smoke", "user", "What is shown?"))
            database.attachmentDao().insertAll(listOf(AttachmentEntity(
                id = "attachment-fixture",
                messageId = "attachment-user",
                displayName = "preview.png",
                mimeType = "image/png",
                localPath = file.absolutePath,
                sizeBytes = file.length()
            )))

            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Image preview").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Image preview").performClick()
            compose.onNodeWithContentDescription("preview.png").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Close image preview").assertIsDisplayed().performClick()
        }
    }
}
