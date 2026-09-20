package io.github.pxldi.schall.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.DownloadRequests
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.downloadTitle
import io.github.pxldi.schall.domain.formatBytes
import io.github.pxldi.schall.ui.common.Controls
import io.github.pxldi.schall.ui.common.ErrorLine
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.ListRow
import io.github.pxldi.schall.ui.common.Listing
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.Meta
import io.github.pxldi.schall.ui.common.Piles
import io.github.pxldi.schall.ui.common.StateLine
import io.github.pxldi.schall.ui.common.rememberAction

private val piles = listOf("open" to "Open", "review" to "Needs review", "failed" to "Failed")

/** The one word the row says about what is happening to the download; the
 * same vocabulary as the web's Downloads screen. */
private fun state(download: DownloadRequest): String = when {
    download.status == "failed" -> "Failed"
    download.status == "cancelled" -> "Cancelled"
    download.importStatus == "needs_review" -> "Needs review"
    download.importStatus == "imported" -> "Imported"
    download.status == "requested" -> if (download.startable) "Ready" else "Waiting"
    download.progress.transferredBytes == 0L && download.progress.queuedCount > 0 -> "Queued at peer"
    download.status == "completed" -> "Checking"
    else -> "Downloading"
}

@Composable
fun DownloadsScreen(openQuestion: (String) -> Unit) {
    val api = LocalApi.current
    var pile by rememberSaveable { mutableStateOf("open") }
    val store = LocalAppStore.current
    val loader = viewModel(store, key = "downloads:$pile") { Loader { api.downloads(pile) } }
    KeepFresh(Stale.Downloads) { loader.refresh() }

    val load by loader.state.collectAsStateWithLifecycle()
    val refreshing by loader.refreshing.collectAsStateWithLifecycle()
    val stale by loader.stale.collectAsStateWithLifecycle()
    val counts = (load as? Load.Ready<DownloadRequests>)?.value?.counts

    Column(Modifier.fillMaxSize()) {
        Piles(
            value = pile,
            options = piles.map { (key, label) ->
                Triple(
                    key,
                    label,
                    when (key) {
                        "open" -> counts?.open
                        "review" -> counts?.review
                        else -> counts?.failed
                    },
                )
            },
            onChange = { pile = it },
        )
        val listed = when (val state = load) {
            is Load.Ready -> Load.Ready(state.value.items)
            Load.Loading -> Load.Loading
            is Load.Failed -> state
        }
        Listing(
            load = listed,
            refreshing = refreshing,
            stale = stale,
            onRefresh = loader::refresh,
            empty = "Nothing here.",
            key = { it.id },
        ) { download -> DownloadRow(download, openQuestion) }
    }
}

@Composable
private fun DownloadRow(download: DownloadRequest, openQuestion: (String) -> Unit) {
    val api = LocalApi.current
    val act = rememberAction()
    var cancelling by rememberSaveable(download.id) { mutableStateOf(false) }
    val (first, second) = downloadTitle(download)
    val progress = download.progress
    val open = download.status == "requested" || download.status == "started"
    val detail = listOf(
        "${progress.completedCount}/${download.fileCount} files",
        "${formatBytes(progress.transferredBytes).ifEmpty { "0 B" }} of ${formatBytes(download.totalSizeBytes).ifEmpty { "?" }}",
        download.username,
    ).joinToString(" · ")

    ListRow(first, second) {
        StateLine(state(download), detail)
        Meta(download.error.orEmpty(), error = true)
        download.peerQuestion?.let { Meta("${it.username} asks: ${it.message}") }
        ErrorLine(act.error)
        Controls {
            if (download.startable) {
                Button(onClick = { act.run(Stale.Downloads) { api.startDownload(download.id) } }, enabled = !act.busy) { Text("Start") }
            }
            if (download.importStatus == "needs_review") {
                Button(onClick = { openQuestion("download:${download.id}") }) { Text("Decide") }
            }
            if (download.retryableCount > 0) {
                OutlinedButton(onClick = { act.run(Stale.Downloads) { api.retryDownload(download.id) } }, enabled = !act.busy) { Text("Retry") }
            }
            if (open) {
                OutlinedButton(onClick = { cancelling = true }, enabled = !act.busy) { Text("Cancel") }
            }
        }
    }

    if (cancelling) {
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = { Text("Cancel this download?") },
            text = { Text(first) },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = false
                    act.run(Stale.Downloads) { api.cancelDownload(download.id) }
                }) { Text("Cancel download") }
            },
            dismissButton = { TextButton(onClick = { cancelling = false }) { Text("Keep") } },
        )
    }
}
