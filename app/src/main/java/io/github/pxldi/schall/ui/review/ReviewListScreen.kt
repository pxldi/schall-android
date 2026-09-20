package io.github.pxldi.schall.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.ReviewItem
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.Question
import io.github.pxldi.schall.domain.downloadTitle
import io.github.pxldi.schall.domain.questions
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.ListRow
import io.github.pxldi.schall.ui.common.Listing
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.StateLine

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

@Composable
fun ReviewListScreen(openQuestion: (String) -> Unit) {
    val (queue, folders) = reviewLoaders()
    KeepFresh(Stale.ReviewQueue, Stale.Downloads) {
        queue.refresh()
        folders.refresh()
    }
    val queueLoad by queue.state.collectAsStateWithLifecycle()
    val foldersLoad by folders.state.collectAsStateWithLifecycle()
    val refreshing by queue.refreshing.collectAsStateWithLifecycle()
    val stale by queue.stale.collectAsStateWithLifecycle()

    val load: Load<List<Question>> = when {
        queueLoad is Load.Failed -> queueLoad as Load.Failed
        foldersLoad is Load.Failed -> foldersLoad as Load.Failed
        queueLoad is Load.Ready && foldersLoad is Load.Ready ->
            Load.Ready(questions((queueLoad as Load.Ready).value, (foldersLoad as Load.Ready).value))
        else -> Load.Loading
    }

    Listing(
        load = load,
        refreshing = refreshing,
        stale = stale,
        onRefresh = {
            queue.refresh()
            folders.refresh()
        },
        empty = "Nothing to decide.",
        key = { it.id },
    ) { question -> QuestionRow(question) { openQuestion(question.id) } }
}

@Composable
private fun QuestionRow(question: Question, onOpen: () -> Unit) {
    when (question) {
        is Question.Folder -> {
            val (first, second) = downloadTitle(question.download)
            ListRow(first, second, Modifier.clickable(onClick = onOpen)) {
                StateLine(question.label, "${question.download.importEvidence?.files?.size ?: 0} files")
            }
        }
        is Question.Downloaded -> {
            val target = question.want.target
            ListRow(target.title, listOf(target.artist, target.album).filter { it.isNotEmpty() }.joinToString(" · "), Modifier.clickable(onClick = onOpen)) {
                StateLine(question.label, "${question.want.copies.count { it.verdict == "held" }} copies")
            }
        }
        is Question.Version -> {
            val target = question.want.target
            ListRow(target.title, listOf(target.artist, target.album).filter { it.isNotEmpty() }.joinToString(" · "), Modifier.clickable(onClick = onOpen)) {
                StateLine(question.label, "${question.want.candidates.size} recordings")
            }
        }
    }
}
