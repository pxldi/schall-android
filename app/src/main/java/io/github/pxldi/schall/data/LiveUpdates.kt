package io.github.pxldi.schall.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** What a server notice says is stale. A notice names a topic, never what the
 * view now holds; the screen refetches. The map is a subset of
 * web/src/lib/events.ts. */
enum class Stale { Downloads, ReviewQueue, Wants, Everything }

private val topics = mapOf(
    "downloads" to listOf(Stale.Downloads),
    "acquisitions" to listOf(Stale.ReviewQueue, Stale.Wants),
)

/** The event stream held open while the app is in front. Each notice is
 * republished to whoever is listening; a screen that owns a list refreshes
 * when its topic arrives. The 15 s poll on each screen is the safety net for
 * a stream that dropped, the same as on the web. */
class LiveUpdates(private val client: OkHttpClient) {
    private val notices = MutableSharedFlow<Stale>(extraBufferCapacity = 16)

    fun notices(vararg of: Stale): Flow<Stale> = notices.filter { it in of || it == Stale.Everything }

    fun invalidate(what: Stale) {
        notices.tryEmit(what)
    }

    /** Runs until cancelled. A dropped stream is retried after a pause that
     * grows to a minute; nothing is refetched on the drop itself, because the
     * connection is down and the poll covers it. */
    suspend fun run(session: Session) {
        var pause = 2_000L
        while (coroutineContext.isActive) {
            val opened = try {
                stream(session).first { true }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                false
            }
            pause = if (opened) 2_000L else minOf(pause * 2, 60_000L)
            delay(pause)
        }
    }

    /** One stream: emits every notice, completes when the stream closes, fails
     * when it could not be opened. `first { true }` above is how the caller
     * learns the stream opened at all, so a stream that closes at once counts
     * as a failure and the pause grows. */
    private fun stream(session: Session): Flow<Unit> = callbackFlow {
        val streaming = client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val request = Request.Builder()
            .url(session.server + "/api/v1/events")
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer ${session.token}")
            .build()
        val source = EventSources.createFactory(streaming).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    trySend(Unit)
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    for (stale in topics[type] ?: emptyList()) notices.tryEmit(stale)
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(t ?: java.io.IOException("event stream failed"))
                }
            },
        )
        awaitClose { source.cancel() }
    }
}
