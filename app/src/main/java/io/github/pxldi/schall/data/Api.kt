package io.github.pxldi.schall.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/** Where the app is signed in: the server's address and the token minted for
 * this phone under Settings → Phone on the web. */
data class Session(val server: String, val token: String)

/** A request the server refused, with what it said. The message is what a
 * screen shows under the control that was pressed. */
class ApiException(val status: Int, message: String, val problem: Problem? = null) : IOException(message)

/** The address as the person typed it, made into something OkHttp accepts: a
 * scheme when none was given, no trailing slash. */
fun normaliseServer(input: String): String {
    var server = input.trim().trimEnd('/')
    if (server.isNotEmpty() && !Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(server)) {
        server = "https://$server"
    }
    return server
}

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Every call the app makes. Paths are the web's, from web/src/lib/api.ts;
 * the words on the controls that post to them are the web's as well. */
class Api(val session: Session, val client: OkHttpClient) {
    /** The wants nobody has to do anything about: the same three states the
     * web's Wants tab reads as one pile. */
    val lookingFor = listOf("unresolved", "pending", "searching")

    fun url(path: String) = session.server + path

    fun coverUrl(albumId: String) = url("/api/v1/albums/${enc(albumId)}/cover")
    fun copyAudioUrl(copyId: String) = url("/api/v1/review-queue/copies/${enc(copyId)}/audio")

    suspend fun me(): Me = get("/api/v1/me")

    suspend fun reviewQueue(limit: Int = 100): ReviewQueue = get("/api/v1/review-queue?limit=$limit&offset=0")
    suspend fun acceptCopy(copyId: String): AcquisitionTarget = post("/api/v1/review-queue/copies/${enc(copyId)}/accept")
    suspend fun acceptFiledCopy(targetId: String): AcquisitionTarget = post("/api/v1/review-queue/wants/${enc(targetId)}/accept-file")
    suspend fun noneOfThese(targetId: String): AcquisitionTarget = post("/api/v1/acquisition-targets/${enc(targetId)}/none-of-these")
    suspend fun wrongSong(targetId: String): AcquisitionTarget = post("/api/v1/acquisition-targets/${enc(targetId)}/wrong-recording")
    suspend fun chooseRecording(targetId: String, recordingId: String): AcquisitionTarget =
        post("/api/v1/acquisition-targets/${enc(targetId)}/resolution", """{"recordingId":${json.encodeToString(serializer(), recordingId)}}""")
    suspend fun stopLooking(targetId: String): AcquisitionTarget = post("/api/v1/acquisition-targets/${enc(targetId)}/not-wanted")
    suspend fun lookAgain(targetId: String): AcquisitionTarget = delete("/api/v1/acquisition-targets/${enc(targetId)}/not-wanted")
    suspend fun wants(statuses: List<String>, limit: Int = 100): AcquisitionTargets =
        get("/api/v1/acquisition-targets?status=${statuses.joinToString(",")}&limit=$limit")

    suspend fun downloads(view: String, limit: Int = 100): DownloadRequests = get("/api/v1/downloads?view=$view&limit=$limit")
    suspend fun startDownload(id: String): DownloadRequest = post("/api/v1/downloads/${enc(id)}/start")
    suspend fun retryDownload(id: String): DownloadRequest = post("/api/v1/downloads/${enc(id)}/retry")
    suspend fun cancelDownload(id: String): DownloadRequest = delete("/api/v1/downloads/${enc(id)}")
    suspend fun revalidateDownload(id: String): DownloadRequest = post("/api/v1/downloads/${enc(id)}/revalidate")
    suspend fun resolveImportTrack(id: String, fileName: String, trackId: String): DownloadRequest =
        post(
            "/api/v1/downloads/${enc(id)}/resolutions",
            """{"fileName":${json.encodeToString(serializer(), fileName)},"trackId":${json.encodeToString(serializer(), trackId)}}""",
        )
    suspend fun withdrawImportResolution(id: String, decisionId: String): DownloadRequest =
        delete("/api/v1/downloads/${enc(id)}/resolutions/${enc(decisionId)}")

    suspend fun searchArtists(query: String): ArtistSearchResults = get("/api/v1/search/artists?q=${enc(query)}")
    suspend fun followArtist(artist: ArtistSearchResult): Artist =
        post(
            "/api/v1/artists",
            """{"musicbrainzId":${json.encodeToString(serializer(), artist.musicbrainzId)},"name":${json.encodeToString(serializer(), artist.name)},"sortName":${json.encodeToString(serializer(), artist.sortName)}}""",
        )

    private suspend inline fun <reified T> get(path: String): T = call(Request.Builder().url(url(path)).get())
    private suspend inline fun <reified T> post(path: String, body: String = ""): T =
        call(Request.Builder().url(url(path)).post(body.toRequestBody(JSON)))
    private suspend inline fun <reified T> delete(path: String): T = call(Request.Builder().url(url(path)).delete())

    private suspend inline fun <reified T> call(builder: Request.Builder): T = withContext(Dispatchers.IO) {
        val request = builder
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${session.token}")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw problem(response.code, text)
            // A 202 or 204 with no body: the decision is recorded and there is
            // nothing to read back. The caller asked for a type it does not use.
            if (text.isBlank()) return@use json.decodeFromString<T>("{}")
            json.decodeFromString<T>(text)
        }
    }

    private fun problem(status: Int, text: String): ApiException {
        val problem = runCatching { json.decodeFromString<Problem>(text) }.getOrNull()
        val message = when {
            status == 401 -> "The server did not accept this token."
            problem?.details?.firstOrNull() != null -> problem.details.first()
            problem?.title != null -> problem.title
            else -> "Request failed ($status)"
        }
        return ApiException(status, message, problem)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** What a screen prints under the control that failed. */
fun Throwable.text(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
