package io.github.pxldi.schall.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.DownloadRequests
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.downloadTitle
import io.github.pxldi.schall.domain.formatBytes
import io.github.pxldi.schall.ui.common.Choice
import io.github.pxldi.schall.ui.common.Choices
import io.github.pxldi.schall.ui.common.Chip
import io.github.pxldi.schall.ui.common.CoverArt
import io.github.pxldi.schall.ui.common.CoverRef
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.QuietButton
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.Tone
import io.github.pxldi.schall.ui.common.rememberAction
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.Type

/** The three piles the phone shows. Copies waiting on a person are on the
 * Review tab, so they are not a fourth pile here. */
private enum class Pile(val view: String, val label: String) {
    Open("open", "Open"),
    Done("imported", "Done"),
    Failed("failed", "Failed"),
}

/** What is happening to the download, in the web's words, with the colour of
 * that state. Null while bytes are arriving: the percentage says it. */
private fun standing(download: DownloadRequest): Pair<String, Tone>? = when {
    download.status == "failed" -> "Failed" to Tone.Fail
    download.status == "cancelled" -> "Cancelled" to Tone.Quiet
    download.importStatus == "needs_review" -> "Needs review" to Tone.Want
    download.importStatus == "imported" -> "Imported" to Tone.Own
    download.status == "requested" -> (if (download.startable) "Ready" else "Waiting") to Tone.Quiet
    download.progress.transferredBytes == 0L && download.progress.queuedCount > 0 -> "Queued" to Tone.Busy
    download.status == "completed" -> "Checking" to Tone.Busy
    else -> null
}

@Composable
fun DownloadsScreen(openQuestion: (String) -> Unit) {
    val api = LocalApi.current
    var pile by rememberSaveable { mutableStateOf(Pile.Open) }
    val store = LocalAppStore.current
    val loader = viewModel(store, key = "downloads:${pile.view}") { Loader { api.downloads(pile.view) } }
    KeepFresh(Stale.Downloads) { loader.refresh() }

    val load by loader.state.collectAsStateWithLifecycle()
    val refreshing by loader.refreshing.collectAsStateWithLifecycle()
    val stale by loader.stale.collectAsStateWithLifecycle()
    val page = (load as? Load.Ready<DownloadRequests>)?.value
    val items = page?.items.orEmpty()

    PrintedPage(
        title = "Downloads",
        covers = items.mapNotNull { it.albumId }.distinct().take(3).map { CoverRef(api.coverUrl(it), it) },
        refreshing = refreshing,
        onRefresh = loader::refresh,
    ) {
        item(key = "piles") {
            Choices {
                Pile.entries.forEach { entry ->
                    val count = page?.counts?.let {
                        when (entry) {
                            Pile.Open -> it.open
                            Pile.Done -> null
                            Pile.Failed -> it.failed
                        }
                    }
                    if (entry == Pile.Failed) {
                        Choice(null, pile == entry, { pile = entry }, icon = Misprint.Alert, count = count, tint = Duoton.fail, description = entry.label)
                    } else {
                        Choice(if (count != null && count > 0) "${entry.label} $count" else entry.label, pile == entry, { pile = entry })
                    }
                }
            }
        }
        (load as? Load.Failed)?.let { item { Said(it.message, error = true) } }
        if (stale.isNotEmpty()) item { Said(stale, error = true) }
        if (page != null && items.isEmpty()) item { Said("Nothing here.") }
        items.forEach { download ->
            item(key = download.id) { DownloadRow(download, openQuestion) }
        }
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
    val review = download.importStatus == "needs_review"
    val mark = standing(download)
    val fraction = if (download.totalSizeBytes > 0) (progress.transferredBytes.toFloat() / download.totalSizeBytes).coerceIn(0f, 1f) else 0f
    val songs = if (download.fileCount == 1) "1 song" else "${download.fileCount} songs"
    val detail = listOf(
        download.username,
        if (open && progress.transferredBytes > 0) "${formatBytes(progress.transferredBytes)} of ${formatBytes(download.totalSizeBytes).ifEmpty { "?" }}"
        else formatBytes(download.totalSizeBytes),
    ).filter { it.isNotEmpty() }.joinToString(" · ")

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (review) Modifier.clickable { openQuestion("download:${download.id}") } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(download.albumId?.let(api::coverUrl), download.albumId ?: first)
            Column(Modifier.weight(1f)) {
                Text(first, style = Type.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(second, songs).filter { it.isNotEmpty() }.joinToString(" · "), style = Type.body, color = Duoton.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (mark != null) Chip(mark.first, mark.second)
            else Text("${(fraction * 100).toInt()}%", style = Type.label, color = Duoton.busy)
        }
        if (mark == null) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Duoton.line)) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Duoton.busy))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail, style = Type.small, color = Duoton.ink3, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (download.startable) {
                QuietButton(null, Misprint.Play, { act.run(Stale.Downloads) { api.startDownload(download.id) } }, enabled = !act.busy, description = "Start", height = 36.dp)
            }
            if (review) {
                QuietButton(null, Misprint.Checklist, { openQuestion("download:${download.id}") }, description = "Decide", tint = Duoton.want, height = 36.dp)
            }
            if (download.retryableCount > 0) {
                QuietButton(null, Misprint.Refresh, { act.run(Stale.Downloads) { api.retryDownload(download.id) } }, enabled = !act.busy, description = "Retry", height = 36.dp)
            }
            if (open) {
                QuietButton(null, Misprint.Close, { cancelling = true }, enabled = !act.busy, description = "Cancel", height = 36.dp)
            }
        }
        download.error?.takeIf { it.isNotEmpty() }?.let { Text(it, style = Type.small, color = Duoton.fail) }
        download.peerQuestion?.let { Text("${it.username} asks: ${it.message}", style = Type.small, color = Duoton.ink2) }
        if (act.error.isNotEmpty()) Text(act.error, style = Type.small, color = Duoton.fail)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Duoton.raise))

    if (cancelling) {
        AlertDialog(
            onDismissRequest = { cancelling = false },
            containerColor = Duoton.panel,
            title = { Text("Cancel this download?", style = Type.name) },
            text = { Text(first, style = Type.body, color = Duoton.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = false
                    act.run(Stale.Downloads) { api.cancelDownload(download.id) }
                }) { Text("Cancel download", color = Duoton.fail) }
            },
            dismissButton = { TextButton(onClick = { cancelling = false }) { Text("Keep", color = Duoton.ink) } },
        )
    }
}
