package io.github.pxldi.schall.ui.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.Api
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.ReviewItem
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.Question
import io.github.pxldi.schall.domain.downloadTitle
import io.github.pxldi.schall.domain.questions
import io.github.pxldi.schall.ui.common.Choice
import io.github.pxldi.schall.ui.common.Choices
import io.github.pxldi.schall.ui.common.CoverArt
import io.github.pxldi.schall.ui.common.CoverRef
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.Row2
import io.github.pxldi.schall.ui.common.Rule
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.StateMark
import io.github.pxldi.schall.ui.common.Tone
import io.github.pxldi.schall.ui.theme.Misprint

/** The two requests the review screen makes, and nothing else: every field a
 * question needs is in these payloads (docs/api.md, "What a review row
 * carries"). Both loaders live in the activity's store, so the list and the
 * question opened from it read one answer. */
@Composable
fun reviewLoaders(): Pair<Loader<List<ReviewItem>>, Loader<List<DownloadRequest>>> {
    val api = LocalApi.current
    val store = LocalAppStore.current
    val queue = viewModel(store, key = "review-queue") { Loader { api.reviewQueue().items } }
    val folders = viewModel(store, key = "downloads:review") { Loader { api.downloads("review").items } }
    return queue to folders
}

/** How many questions are waiting, for the badge on the tab. */
@Composable
fun reviewCount(): Int {
    val (queue, folders) = reviewLoaders()
    val queueLoad by queue.state.collectAsStateWithLifecycle()
    val foldersLoad by folders.state.collectAsStateWithLifecycle()
    return questions((queueLoad as? Load.Ready)?.value, (foldersLoad as? Load.Ready)?.value).size
}

/** What a question is about, for its row and its page. */
data class QuestionFace(val title: String, val subtitle: String, val cover: CoverRef, val icon: Misprint)

fun face(question: Question, api: Api): QuestionFace = when (question) {
    is Question.Folder -> {
        val (first, second) = downloadTitle(question.download)
        val files = question.download.importEvidence?.files?.size ?: 0
        QuestionFace(
            first,
            listOf(second, if (files == 1) "1 file" else "$files files").filter { it.isNotEmpty() }.joinToString(" · "),
            CoverRef(question.download.albumId?.let(api::coverUrl), question.download.albumId ?: first),
            Misprint.FolderAdd,
        )
    }
    is Question.Downloaded -> {
        val target = question.want.target
        val copies = question.want.copies.count { it.verdict == "held" }
        QuestionFace(
            target.title,
            listOf(target.artist, if (copies == 1) "1 copy" else "$copies copies").filter { it.isNotEmpty() }.joinToString(" · "),
            CoverRef(target.originAlbumId?.let(api::coverUrl), target.originAlbumId ?: target.id),
            Misprint.Download,
        )
    }
    is Question.Version -> {
        val target = question.want.target
        val recordings = question.want.candidates.size
        QuestionFace(
            target.title,
            listOf(target.artist, if (recordings == 1) "1 recording" else "$recordings recordings").filter { it.isNotEmpty() }.joinToString(" · "),
            CoverRef(target.originAlbumId?.let(api::coverUrl), target.originAlbumId ?: target.id),
            Misprint.Fingerprint,
        )
    }
}

private enum class Kind(val label: String, val icon: Misprint?) {
    All("All", null),
    Downloaded("Downloaded", Misprint.Download),
    Version("Version", Misprint.Fingerprint),
    Folder("Folder", Misprint.FolderAdd),
}

private fun Question.kind() = when (this) {
    is Question.Downloaded -> Kind.Downloaded
    is Question.Version -> Kind.Version
    is Question.Folder -> Kind.Folder
}

@Composable
fun ReviewListScreen(openQuestion: (String) -> Unit) {
    val api = LocalApi.current
    val (queue, folders) = reviewLoaders()
    KeepFresh(Stale.ReviewQueue, Stale.Downloads) {
        queue.refresh()
        folders.refresh()
    }
    val queueLoad by queue.state.collectAsStateWithLifecycle()
    val foldersLoad by folders.state.collectAsStateWithLifecycle()
    val refreshing by queue.refreshing.collectAsStateWithLifecycle()
    val stale by queue.stale.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(Kind.All) }

    val all = questions((queueLoad as? Load.Ready)?.value, (foldersLoad as? Load.Ready)?.value)
    val shown = if (kind == Kind.All) all else all.filter { it.kind() == kind }
    val faces = shown.map { it to face(it, api) }
    val failed = (queueLoad as? Load.Failed)?.message ?: (foldersLoad as? Load.Failed)?.message
    val loading = queueLoad is Load.Loading || foldersLoad is Load.Loading

    PrintedPage(
        title = "Review",
        covers = faces.map { it.second.cover }.distinctBy { it.seed }.take(2),
        subtitle = when {
            loading -> ""
            all.size == 1 -> "1 song to decide"
            else -> "${all.size} songs to decide"
        },
        refreshing = refreshing,
        onRefresh = {
            queue.refresh()
            folders.refresh()
        },
    ) {
        item(key = "kinds") {
            Choices {
                Kind.entries.forEach { entry ->
                    val count = if (entry == Kind.All) null else all.count { it.kind() == entry }
                    if (entry != Kind.All && count == 0) return@forEach
                    Choice(
                        label = if (entry.icon == null) entry.label else null,
                        selected = kind == entry,
                        onClick = { kind = entry },
                        icon = entry.icon,
                        count = count,
                        description = entry.label,
                    )
                }
            }
        }
        if (failed != null) item { Said(failed, error = true) }
        if (stale.isNotEmpty()) item { Said(stale, error = true) }
        if (!loading && failed == null && shown.isEmpty()) item { Said("Nothing to decide.") }
        faces.forEach { (question, face) ->
            item(key = question.id) {
                Row2(
                    title = face.title,
                    subtitle = face.subtitle,
                    onClick = { openQuestion(question.id) },
                    leading = { CoverArt(face.cover.url, face.cover.seed) },
                ) { StateMark(face.icon, Tone.Want, question.label) }
                Rule()
            }
        }
    }
}
