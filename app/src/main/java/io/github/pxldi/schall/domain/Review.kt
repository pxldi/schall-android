package io.github.pxldi.schall.domain

import io.github.pxldi.schall.data.AcquiredCopy
import io.github.pxldi.schall.data.CopyGroup
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.ImportEvidence
import io.github.pxldi.schall.data.ImportFileEvidence
import io.github.pxldi.schall.data.ImportTags
import io.github.pxldi.schall.data.ReviewItem

/** One question on the review screen. The web draws the same three kinds and
 * no others: a want with copies to hear, a want with recordings to choose
 * between, and an album folder whose files did not all match. */
sealed class Question {
    abstract val id: String
    abstract val label: String

    data class Downloaded(val want: ReviewItem) : Question() {
        override val id = "want:${want.target.id}"
        override val label = "Downloaded"
    }

    data class Version(val want: ReviewItem) : Question() {
        override val id = "want:${want.target.id}"
        override val label = "Version"
    }

    data class Folder(val download: DownloadRequest) : Question() {
        override val id = "download:${download.id}"
        override val label = "Folder"
    }
}

fun questions(queue: List<ReviewItem>?, folders: List<DownloadRequest>?): List<Question> {
    val out = ArrayList<Question>()
    for (want in queue.orEmpty()) {
        out += if (want.kind == "resolution") Question.Version(want) else Question.Downloaded(want)
    }
    for (download in folders.orEmpty()) {
        if (download.importEvidence != null) out += Question.Folder(download)
    }
    return out
}

/** The copies the server measured as the same audio, read as one card. */
data class CopyGroupView(val best: AcquiredCopy, val others: List<AcquiredCopy>, val rips: Int)

/** A held copy is one a person may still accept; a better bit rate is the
 * better rip of the same audio. The order is how the cards are read, and says
 * nothing about what may be accepted. */
private fun rank(copies: List<AcquiredCopy>): List<AcquiredCopy> = copies.sortedWith(
    compareByDescending<AcquiredCopy> { it.verdict == "held" }
        .thenByDescending { it.evidence?.bitRate ?: 0 }
        .thenByDescending { it.sizeBytes ?: 0 },
)

/** The web's grouping: copies the server measured as the same audio read as
 * one card. Accept is about the card's best copy; None of these refuses them
 * all. A queue that sent no groups gives one card per copy. */
fun groupCopies(copies: List<AcquiredCopy>, groups: List<CopyGroup>?): List<CopyGroupView> {
    val byId = copies.associateBy { it.id }.toMutableMap()
    val views = ArrayList<CopyGroupView>()
    for (group in groups.orEmpty()) {
        val members = rank(group.copyIds.mapNotNull { byId[it] })
        if (members.isEmpty()) continue
        for (member in members) byId.remove(member.id)
        views += CopyGroupView(members[0], members.drop(1), if (group.rips > 0) group.rips else members.size)
    }
    for (copy in copies) {
        if (!byId.containsKey(copy.id)) continue
        byId.remove(copy.id)
        views += CopyGroupView(copy, emptyList(), 1)
    }
    val order = rank(views.map { it.best })
    return views.sortedBy { order.indexOf(it.best) }
}

/** The two lines a download is named by: the release and its artist, or the
 * wanted entry and its artist. */
fun downloadTitle(download: DownloadRequest): Pair<String, String> {
    if (!download.albumTitle.isNullOrEmpty()) return download.albumTitle to download.artistName.orEmpty()
    return (download.entryTitle ?: download.directory) to download.entryArtist.orEmpty()
}

/** Every catalogue track the evidence mentions, in playing order. A file can
 * only be resolved to a track of the release it was requested for, so this is
 * the whole set of answers the folder screen can give. */
fun importCatalogue(evidence: ImportEvidence?): List<ImportTags> {
    val byId = LinkedHashMap<String, ImportTags>()
    val mentioned = evidence?.files.orEmpty().map { it.expected } + evidence?.unmatchedTracks.orEmpty()
    for (tags in mentioned) {
        val id = tags?.trackId ?: continue
        byId[id] = tags
    }
    return byId.values.sortedWith(compareBy<ImportTags> { it.discNumber ?: 1 }.thenBy { it.trackNumber ?: 0 })
}

/** Files that disagree are the reason the question exists, so they lead. */
fun orderFiles(files: List<ImportFileEvidence>): List<ImportFileEvidence> =
    files.filter { it.problems.isNotEmpty() } + files.filter { it.problems.isEmpty() }

/** The answer the evidence came closest to, so the common case is confirming
 * a judgement. Nothing is admitted by it: a person still presses Resolve. */
fun suggestedTrack(file: ImportFileEvidence, catalogue: List<ImportTags>): String =
    file.expected?.trackId
        ?: file.candidates?.firstOrNull { it.takenBy == null }?.track?.trackId
        ?: catalogue.firstOrNull()?.trackId
        ?: ""

fun trackLabel(track: ImportTags): String {
    val number = track.trackNumber?.let { n ->
        val disc = track.discNumber?.takeIf { it > 1 }?.let { "$it-" } ?: ""
        "$disc$n."
    } ?: ""
    return listOf(number, track.title ?: "Untitled").filter { it.isNotEmpty() }.joinToString(" ")
}
