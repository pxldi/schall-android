package io.github.pxldi.schall.data

import kotlinx.serialization.Serializable

// The server's JSON, as the web's api-types.ts declares it. Only the fields a
// screen reads are named; everything else is ignored on the way in. A field
// the server may leave out is nullable with a default, so an older or newer
// server never fails to parse.

@Serializable
data class Me(val actor: String, val auth: String)

@Serializable
data class ReviewQueue(val items: List<ReviewItem> = emptyList(), val total: Int = 0)

@Serializable
data class ReviewItem(
    val kind: String,
    val target: AcquisitionTarget,
    val copies: List<AcquiredCopy> = emptyList(),
    val candidates: List<AcquisitionCandidate> = emptyList(),
    val ruledOut: Int = 0,
    val fileRecordingId: String? = null,
    val fileTitle: String? = null,
    val fileArtist: String? = null,
    val fileReleaseTitle: String? = null,
    val copyGroups: List<CopyGroup>? = null,
    val waitingFor: String? = null,
    val waitingUntil: String? = null,
)

@Serializable
data class AcquisitionTarget(
    val id: String,
    val origin: String = "",
    val artist: String = "",
    val title: String = "",
    val album: String = "",
    val durationMs: Long? = null,
    val isrc: String? = null,
    val originAlbumId: String? = null,
    val recordingId: String? = null,
    val status: String = "",
    val summary: String = "",
    val attempts: Int = 0,
    val lastAttemptAt: String? = null,
    val nextAttemptAt: String? = null,
    val lastError: String? = null,
    val waitingOnYou: Boolean = false,
)

@Serializable
data class AcquisitionTargets(
    val items: List<AcquisitionTarget> = emptyList(),
    val total: Int = 0,
    val notice: String? = null,
)

@Serializable
data class AcquiredCopy(
    val id: String,
    val provider: String = "",
    val username: String = "",
    val name: String = "",
    val sizeBytes: Long? = null,
    val verdict: String = "",
    val decidedBy: String = "",
    val summary: String = "",
    val evidence: AcquiredCopyEvidence? = null,
    val libraryFileId: String? = null,
)

@Serializable
data class AcquiredCopyEvidence(
    val observed: ImportTags? = null,
    val wanted: ImportTags? = null,
    val agrees: List<String> = emptyList(),
    val differs: List<String> = emptyList(),
    val problems: List<String> = emptyList(),
    val sizeBytes: Long? = null,
    val bitRate: Int? = null,
)

@Serializable
data class CopyGroup(val copyIds: List<String> = emptyList(), val rips: Int = 0)

@Serializable
data class AcquisitionCandidate(
    val recordingId: String,
    val releaseGroupId: String? = null,
    val artistName: String = "",
    val releaseTitle: String? = null,
    val trackTitle: String = "",
    val durationMs: Long? = null,
    val isrc: String? = null,
    val agrees: List<String> = emptyList(),
    val differs: List<String> = emptyList(),
    val summary: String = "",
)

@Serializable
data class ImportTags(
    val trackId: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val durationMs: Long? = null,
)

@Serializable
data class DownloadRequests(
    val items: List<DownloadRequest> = emptyList(),
    val total: Int = 0,
    val counts: DownloadCounts = DownloadCounts(),
)

@Serializable
data class DownloadCounts(
    val open: Int = 0,
    val review: Int = 0,
    val imported: Int = 0,
    val discarded: Int = 0,
    val failed: Int = 0,
    val all: Int = 0,
)

@Serializable
data class DownloadRequest(
    val id: String,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val artistName: String? = null,
    val acquisitionTargetId: String? = null,
    val entryArtist: String? = null,
    val entryTitle: String? = null,
    val username: String = "",
    val directory: String = "",
    val status: String = "",
    val fileCount: Int = 0,
    val totalSizeBytes: Long = 0,
    val progress: DownloadProgress = DownloadProgress(),
    val startable: Boolean = false,
    val retryableCount: Int = 0,
    val error: String? = null,
    val importStatus: String = "",
    val importError: String? = null,
    val importEvidence: ImportEvidence? = null,
    val importDecisions: List<ImportDecision> = emptyList(),
    val revalidatable: Boolean = false,
    val peerQuestion: PeerQuestion? = null,
)

@Serializable
data class DownloadProgress(
    val transferCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val queuedCount: Int = 0,
    val transferredBytes: Long = 0,
)

@Serializable
data class PeerQuestion(val username: String = "", val message: String = "", val askedAt: String = "")

@Serializable
data class ImportEvidence(
    val files: List<ImportFileEvidence> = emptyList(),
    val unmatchedTracks: List<ImportTags> = emptyList(),
    val problems: List<String> = emptyList(),
)

@Serializable
data class ImportFileEvidence(
    val name: String,
    val position: String = "",
    val observed: ImportTags? = null,
    val expected: ImportTags? = null,
    val match: ImportMatch? = null,
    val candidates: List<ImportCandidate>? = null,
    val problems: List<String> = emptyList(),
    val resolvable: Boolean = false,
)

@Serializable
data class ImportMatch(val method: String = "", val summary: String = "")

@Serializable
data class ImportCandidate(
    val track: ImportTags,
    val agrees: List<String> = emptyList(),
    val differs: List<String> = emptyList(),
    val takenBy: String? = null,
)

@Serializable
data class ImportDecision(
    val id: String,
    val fileName: String,
    val trackId: String,
    val trackTitle: String = "",
    val decidedAt: String = "",
)

@Serializable
data class ArtistSearchResults(val items: List<ArtistSearchResult> = emptyList())

@Serializable
data class ArtistSearchResult(
    val musicbrainzId: String,
    val name: String,
    val sortName: String = "",
    val type: String? = null,
    val country: String? = null,
    val area: String? = null,
    val disambiguation: String? = null,
)

@Serializable
data class Artist(val id: String, val name: String = "", val followed: Boolean = false)

@Serializable
data class Problem(val title: String? = null, val details: List<String>? = null)

/** A release's stored inks (ADR Duoton), as the server sends them. */
@Serializable
data class InksJson(val dark: String = "", val light: String = "", val palette: List<String>? = null) {
    fun toInks() = io.github.pxldi.schall.domain.Inks(dark, light, palette)
}

/** The Overview's figures, the parts the phone shows. */
@Serializable
data class Overview(
    val arrived: OverviewArrived = OverviewArrived(),
    val library: OverviewLibrary = OverviewLibrary(),
    val recentlyAdded: List<OverviewAdded> = emptyList(),
)

@Serializable
data class OverviewArrived(val today: Int = 0, val downloading: Int = 0)

@Serializable
data class OverviewLibrary(val fileCount: Int = 0, val totalBytes: Long = 0)

@Serializable
data class OverviewAdded(
    val title: String = "",
    val artist: String = "",
    val addedAt: String = "",
    val trackId: String? = null,
    val releaseId: String? = null,
    val coverUrl: String? = null,
    val inks: InksJson? = null,
)

/** The library search, as the web's search popup reads it. Files and
 * playlists come back too; the phone shows artists, releases and songs. */
@Serializable
data class GlobalSearch(
    val artists: List<FoundArtist> = emptyList(),
    val releases: List<FoundRelease> = emptyList(),
    val tracks: List<FoundTrack> = emptyList(),
)

@Serializable
data class FoundArtist(
    val id: String,
    val name: String = "",
    val followed: Boolean = false,
    val fileCount: Int = 0,
    val ownedReleases: Int = 0,
    val countedReleases: Int = 0,
)

@Serializable
data class FoundRelease(
    val id: String,
    val title: String = "",
    val artistName: String = "",
    val albumType: String = "",
    val releaseDate: String? = null,
    val trackCount: Int = 0,
    val ownedTrackCount: Int = 0,
    val inks: InksJson? = null,
)

@Serializable
data class FoundTrack(
    val id: String,
    val title: String = "",
    val durationMs: Long? = null,
    val albumId: String = "",
    val albumTitle: String = "",
    val artistName: String = "",
    val owned: Boolean = false,
    val wantStatus: String? = null,
)

@Serializable
data class Discography(val releases: Int = 0, val owned: Int = 0, val missing: Int = 0, val counted: Int = 0)

@Serializable
data class ArtistDetail(
    val id: String,
    val name: String = "",
    val followed: Boolean = false,
    val monitorLevel: String = "everything",
    val wantMissing: Boolean = false,
    val discography: Discography = Discography(),
    val genres: List<String> = emptyList(),
    val leadAlbumId: String? = null,
    val inks: InksJson? = null,
)

@Serializable
data class Release(
    val id: String,
    val title: String = "",
    val firstReleaseDate: String? = null,
    val albumType: String = "",
    val secondaryTypes: List<String> = emptyList(),
    val trackCount: Int = 0,
    val ownedTrackCount: Int = 0,
    val dismissedTrackCount: Int = 0,
    val wantedTrackCount: Int = 0,
    val leavingTrackCount: Int = 0,
    val artistMonitorLevel: String = "",
    val hasCover: Boolean = false,
    val inks: InksJson? = null,
)

@Serializable
data class ReleaseList(val items: List<Release> = emptyList(), val total: Int = 0)

@Serializable
data class ReleaseDetail(
    val id: String,
    val artistId: String = "",
    val artistName: String = "",
    val artistFollowed: Boolean = false,
    val title: String = "",
    val firstReleaseDate: String? = null,
    val albumType: String = "",
    val mediaCount: Int = 1,
    val trackCount: Int = 0,
    val trackRefreshStatus: String = "",
    val inks: InksJson? = null,
)

@Serializable
data class Track(
    val id: String,
    val musicbrainzRecordingId: String? = null,
    val title: String = "",
    val discNumber: Int = 1,
    val trackNumber: Int? = null,
    val durationMs: Long? = null,
    val owned: Boolean = false,
    val heldElsewhere: Boolean = false,
    val wantId: String? = null,
    val wantStatus: String? = null,
    val removesAt: String? = null,
)

@Serializable
data class TrackList(val items: List<Track> = emptyList())

/** What a want or a removal answered. The screens only refetch on it. */
@Serializable
data class Done(val removed: Int = 0, val wanted: Int = 0)
