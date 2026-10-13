package io.github.pxldi.schall.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pxldi.schall.SchallApp
import io.github.pxldi.schall.data.StoredSession
import io.github.pxldi.schall.ui.theme.SchallTheme
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the signed-in app on the JVM against fixed answers, and writes
 * each screen to app/build/screens so a change to the look can be seen before
 * it reaches a phone. Covers are not served, so every cover is the generated
 * stand-in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val answers = mapOf(
        "/api/v1/overview" to """
            {"arrived":{"today":2,"downloading":3},"library":{"fileCount":5120,"totalBytes":152000000000},
             "recentlyAdded":[
              {"title":"Ultraviolet","artist":"Neon Psalms","addedAt":"2026-10-12T10:00:00Z","releaseId":"r21","inks":{"dark":"#1a0a3a","light":"#d38cff"}},
              {"title":"Beacon One","artist":"Vela Nine","addedAt":"2026-10-11T10:00:00Z","releaseId":"r0","inks":{"dark":"#1f4a33","light":"#bfe8c9"}},
              {"title":"Concrete Garden","artist":"Lotte Brandt","addedAt":"2026-10-10T10:00:00Z","releaseId":"r12","inks":{"dark":"#2a2d2f","light":"#d9f26b"}}
             ]}
        """,
        "/api/v1/review-queue" to """
            {"total":2,"items":[
             {"kind":"copies","target":{"id":"t1","artist":"Vela Nine","title":"Watchfire","album":"Signal Fires","durationMs":302000,"status":"review"},
              "copies":[{"id":"c1","username":"harbourmaster","name":"Watchfire.flac","sizeBytes":38200000,"verdict":"held"}]},
             {"kind":"resolution","target":{"id":"t2","artist":"Kestrel Grove","title":"Evensong (single edit)","album":"Harrow Bell","durationMs":212000,"status":"review"},
              "candidates":[{"recordingId":"m1","artistName":"Kestrel Grove","trackTitle":"Evensong","releaseTitle":"Harrow Bell","durationMs":287000}]}
            ]}
        """,
        "/api/v1/downloads" to """
            {"total":2,"counts":{"open":2,"review":0,"imported":7,"failed":1},"items":[
             {"id":"d1","albumTitle":"Signal Fires","artistName":"Vela Nine","username":"harbourmaster","status":"started","fileCount":4,"totalSizeBytes":145000000,
              "progress":{"transferCount":4,"completedCount":0,"transferredBytes":18000000}},
             {"id":"d2","albumTitle":"Concrete Garden","artistName":"Lotte Brandt","username":"kiezfunk","status":"started","fileCount":1,"totalSizeBytes":41000000,
              "progress":{"transferCount":1,"queuedCount":1,"transferredBytes":0}}
            ]}
        """,
        "/api/v1/acquisition-targets" to """
            {"total":2,"items":[
             {"id":"w1","artist":"Kestrel Grove","title":"Lantern Year","status":"pending","attempts":2,"lastAttemptAt":"2026-10-12T22:00:00Z"},
             {"id":"w2","artist":"Neon Psalms","title":"Afterglow Static","status":"searching"}
            ]}
        """,
    )

    private val http = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            val path = chain.request().url.encodedPath
            val body = answers[path]
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (body != null) 200 else 404)
                .message(if (body != null) "OK" else "Not Found")
                .body((body ?: """{"title":"Not found"}""").trimIndent().toResponseBody("application/json".toMediaType()))
                .build()
        },
    ).build()

    private fun show() {
        val app = ApplicationProvider.getApplicationContext<SchallApp>()
        compose.setContent {
            SchallTheme {
                SignedIn(app, StoredSession("https://schall.test", "token", "phone"), emptyFlow(), http)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun overview() {
        show()
        compose.onRoot().captureRoboImage("build/screens/overview.png")
    }

    @Test
    fun review() {
        show()
        // The figure on the Overview and the tab both open Review.
        compose.onAllNodesWithText("Review").onFirst().performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/review.png")
    }

    @Test
    fun question() {
        show()
        compose.onAllNodesWithText("Review").onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Watchfire").onFirst().performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/question.png")
    }

    @Test
    fun version() {
        show()
        compose.onAllNodesWithText("Review").onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Evensong (single edit)").onFirst().performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/version.png")
    }

    @Test
    fun downloads() {
        show()
        compose.onAllNodesWithText("Downloads").onFirst().performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/downloads.png")
    }

    @Test
    fun wishlist() {
        show()
        compose.onAllNodesWithText("Wishlist").onFirst().performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/wishlist.png")
    }

    @Test
    fun settings() {
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/settings.png")
    }
}
