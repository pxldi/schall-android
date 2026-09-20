@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.pxldi.schall.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pxldi.schall.data.AcquisitionCandidate
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.ImportFileEvidence
import io.github.pxldi.schall.data.ImportTags
import io.github.pxldi.schall.data.ReviewItem
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.CopyGroupView
import io.github.pxldi.schall.domain.Question
import io.github.pxldi.schall.domain.downloadTitle
import io.github.pxldi.schall.domain.formatBytes
import io.github.pxldi.schall.domain.formatDuration
import io.github.pxldi.schall.domain.groupCopies
import io.github.pxldi.schall.domain.importCatalogue
import io.github.pxldi.schall.domain.orderFiles
import io.github.pxldi.schall.domain.questions
import io.github.pxldi.schall.domain.suggestedTrack
import io.github.pxldi.schall.domain.trackLabel
import io.github.pxldi.schall.ui.common.Action
import io.github.pxldi.schall.ui.common.Centered
import io.github.pxldi.schall.ui.common.Cover
import io.github.pxldi.schall.ui.common.ErrorLine
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.Meta
import io.github.pxldi.schall.ui.common.StateChip
import io.github.pxldi.schall.ui.common.rememberAction

/** One question, its evidence, and the same answers the web offers in the
 * same words. Answering goes back to the list; the list refetches, so the
 * next question is whatever the server says is next. */
@Composable
fun ReviewQuestionScreen(id: String, back: () -> Unit) {
    val (queue, folders) = reviewLoaders()
    KeepFresh(Stale.ReviewQueue, Stale.Downloads) {
        queue.refresh()
        folders.refresh()
    }
    val queueLoad by queue.state.collectAsStateWithLifecycle()
    val foldersLoad by folders.state.collectAsStateWithLifecycle()
    val loading = queueLoad is Load.Loading || foldersLoad is Load.Loading
    val question = questions((queueLoad as? Load.Ready)?.value, (foldersLoad as? Load.Ready)?.value).firstOrNull { it.id == id }
    val decide = rememberAction()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(question?.label ?: "Review") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                question is Question.Folder -> FolderQuestion(question.download, decide, back)
                question is Question.Downloaded -> WantQuestion(question.want, version = false, decide, back)
                question is Question.Version -> WantQuestion(question.want, version = true, decide, back)
                loading -> Centered { CircularProgressIndicator() }
                else -> Centered { Text("This question has been answered.") }
            }
        }
    }
}

@Composable
private fun Head(cover: String?, label: String, title: String, second: String, third: String) {
    val api = LocalApi.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Cover(cover, api.session.token)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            StateChip(label)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Meta(second)
            Meta(third)
        }
    }
}

@Composable
private fun WantQuestion(want: ReviewItem, version: Boolean, decide: Action, back: () -> Unit) {
    val api = LocalApi.current
    val target = want.target
    val filed = want.kind == "stopped" && want.fileRecordingId != null
    val groups = remember(want) {
        groupCopies(want.copies.filter { it.verdict == "held" || want.kind == "stopped" }, want.copyGroups)
    }
    var chosen by rememberSaveable(want.target.id) { mutableStateOf<String?>(null) }
    var removing by rememberSaveable(want.target.id) { mutableStateOf(false) }
    fun answered(block: suspend () -> Unit) = decide.run(Stale.ReviewQueue, Stale.Downloads, Stale.Wants, done = back, block = block)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Head(
            cover = target.originAlbumId?.let { api.coverUrl(it) },
            label = if (version) "Version" else "Downloaded",
            title = target.title,
            second = target.artist,
            third = listOfNotNull(target.album.ifEmpty { null }, formatDuration(target.durationMs).ifEmpty { null }, target.isrc).joinToString(" · "),
        )

        if (!version && !filed) {
            Player(copy = groups.firstOrNull { it.best.id == chosen }?.best)
            if (groups.isEmpty()) Meta("No copy is left to hear.")
            groups.forEach { group ->
                CopyCard(group, chosen = chosen == group.best.id) { chosen = group.best.id }
            }
        }

        if (!version && filed) {
            OutlinedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Already a file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(want.fileTitle.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Meta(listOfNotNull(want.fileArtist, want.fileReleaseTitle).joinToString(" · "))
                    Meta("The library calls this file another recording. Accept says it is this one.")
                }
            }
        }

        if (version) {
            want.candidates.forEach { candidate ->
                CandidateCard(candidate, chosen = chosen == candidate.recordingId) { chosen = candidate.recordingId }
            }
        }

        if (want.ruledOut > 0) Meta("${want.ruledOut} ruled out already.")
        ErrorLine(decide.error)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!version && !filed) {
                Button(onClick = { chosen?.let { copy -> answered { api.acceptCopy(copy) } } }, enabled = !decide.busy && chosen != null) { Text("Accept") }
            }
            if (!version && filed) {
                Button(onClick = { answered { api.acceptFiledCopy(target.id) } }, enabled = !decide.busy) { Text("Accept") }
            }
            if (version) {
                Button(onClick = { chosen?.let { recording -> answered { api.chooseRecording(target.id, recording) } } }, enabled = !decide.busy && chosen != null) { Text("Use") }
            }
            if (!version && !filed) {
                OutlinedButton(onClick = { answered { api.noneOfThese(target.id) } }, enabled = !decide.busy) { Text("None of these") }
            }
            if (!version) {
                OutlinedButton(onClick = { answered { api.wrongSong(target.id) } }, enabled = !decide.busy) { Text("Wrong song") }
            }
            TextButton(onClick = { removing = true }, enabled = !decide.busy) { Text("Remove from Wishlist") }
        }
    }

    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove from Wishlist?") },
            text = { Text("${target.artist} – ${target.title} stops being looked for.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = false
                    answered { api.stopLooking(target.id) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ChoiceCard(chosen: Boolean, onChoose: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = chosen, onClick = onChoose, role = Role.RadioButton),
        colors = CardDefaults.cardColors(
            containerColor = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = chosen, onClick = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        }
    }
}

@Composable
private fun CopyCard(group: CopyGroupView, chosen: Boolean, onChoose: () -> Unit) {
    val copy = group.best
    val evidence = copy.evidence
    ChoiceCard(chosen, onChoose) {
        Text(copy.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        Meta(
            listOfNotNull(
                evidence?.bitRate?.let { "$it kbps" },
                formatBytes(copy.sizeBytes ?: evidence?.sizeBytes).ifEmpty { null },
                evidence?.observed?.durationMs?.let { formatDuration(it) },
                if (group.rips > 1) "${group.rips} rips of this audio" else null,
                copy.username.ifEmpty { null },
            ).joinToString(" · "),
        )
        if (!evidence?.agrees.isNullOrEmpty()) Meta("Agrees: ${evidence.agrees.joinToString(", ")}")
        if (!evidence?.differs.isNullOrEmpty()) Meta("Differs: ${evidence.differs.joinToString(", ")}", error = true)
        Meta(copy.summary, maxLines = 4)
    }
}

@Composable
private fun CandidateCard(candidate: AcquisitionCandidate, chosen: Boolean, onChoose: () -> Unit) {
    ChoiceCard(chosen, onChoose) {
        Text(candidate.trackTitle, style = MaterialTheme.typography.bodyLarge)
        Meta(listOfNotNull(candidate.artistName.ifEmpty { null }, candidate.releaseTitle).joinToString(" · "))
        Meta(listOfNotNull(formatDuration(candidate.durationMs).ifEmpty { null }, candidate.isrc).joinToString(" · "))
        if (candidate.agrees.isNotEmpty()) Meta("Agrees: ${candidate.agrees.joinToString(", ")}")
        if (candidate.differs.isNotEmpty()) Meta("Differs: ${candidate.differs.joinToString(", ")}", error = true)
    }
}

/** An album folder whose files did not all match. Each file that stopped the
 * import can be named as one track of the release; that settles the file
 * without leaving the folder, because there is usually another file left, so
 * it refetches instead of going back. Check again runs the import with the
 * decisions applied. */
@Composable
private fun FolderQuestion(download: DownloadRequest, decide: Action, back: () -> Unit) {
    val api = LocalApi.current
    val settle = rememberAction()
    val evidence = download.importEvidence
    val catalogue = remember(evidence) { importCatalogue(evidence) }
    val files = remember(evidence) { orderFiles(evidence?.files.orEmpty()) }
    val (first, second) = downloadTitle(download)
    val missing = evidence?.unmatchedTracks.orEmpty()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Head(
            cover = download.albumId?.let { api.coverUrl(it) },
            label = "Folder",
            title = first,
            second = second,
            third = "${download.username} · ${download.fileCount} files",
        )
        Meta(download.importError.orEmpty(), error = true)
        evidence?.problems?.forEach { Meta(it, error = true) }
        files.forEach { file ->
            FileCard(
                file = file,
                catalogue = catalogue,
                decision = download.importDecisions.firstOrNull { it.fileName == file.name },
                busy = settle.busy,
                onResolve = { trackId -> settle.run(Stale.Downloads) { api.resolveImportTrack(download.id, file.name, trackId) } },
                onWithdraw = { decisionId -> settle.run(Stale.Downloads) { api.withdrawImportResolution(download.id, decisionId) } },
            )
        }
        if (missing.isNotEmpty()) Meta("No file for ${missing.joinToString(", ") { trackLabel(it) }}.", maxLines = 6)
        ErrorLine(settle.error.ifEmpty { decide.error })
        Row {
            OutlinedButton(
                onClick = { decide.run(Stale.Downloads, Stale.ReviewQueue, done = back) { api.revalidateDownload(download.id) } },
                enabled = !decide.busy,
            ) { Text("Check again") }
        }
    }
}

/** One file of the folder. A file that matched says how; one that did not
 * lists the tracks it could be, the evidence's own candidates first and the
 * rest of the release behind "All tracks". */
@Composable
private fun FileCard(
    file: ImportFileEvidence,
    catalogue: List<ImportTags>,
    decision: io.github.pxldi.schall.data.ImportDecision?,
    busy: Boolean,
    onResolve: (String) -> Unit,
    onWithdraw: (String) -> Unit,
) {
    var chosen by rememberSaveable(file.name) { mutableStateOf("") }
    var all by rememberSaveable(file.name) { mutableStateOf(false) }
    val open = file.problems.isNotEmpty() && decision == null
    val selected = chosen.ifEmpty { suggestedTrack(file, catalogue) }
    val candidateIds = file.candidates.orEmpty().mapNotNull { it.track.trackId }.toMutableSet()
    file.expected?.trackId?.let { candidateIds += it }
    val shown = if (all || candidateIds.isEmpty()) catalogue else catalogue.filter { it.trackId in candidateIds }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            when {
                decision != null -> Meta("Resolved as “${decision.trackTitle}”. Check again to apply it.")
                file.match != null -> Meta(file.match.summary)
            }
            if (open) file.problems.forEach { Meta(it, error = true) }
            if (open && file.resolvable && catalogue.isNotEmpty()) {
                Text("This file is", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                shown.forEach { track ->
                    val candidate = file.candidates?.firstOrNull { it.track.trackId == track.trackId }
                    val checked = selected == track.trackId
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = checked, onClick = { chosen = track.trackId.orEmpty() }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = checked, onClick = null)
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(trackLabel(track), style = MaterialTheme.typography.bodyMedium)
                            if (!candidate?.agrees.isNullOrEmpty()) Meta("Agrees: ${candidate.agrees.joinToString(", ")}")
                            if (!candidate?.differs.isNullOrEmpty()) Meta("Differs: ${candidate.differs.joinToString(", ")}", error = true)
                            candidate?.takenBy?.let { Meta("Already matched by $it") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onResolve(selected) }, enabled = !busy && selected.isNotEmpty()) { Text("Resolve") }
                    if (!all && shown.size < catalogue.size) TextButton(onClick = { all = true }) { Text("All tracks") }
                }
            }
            if (open && !file.resolvable) Meta("Not a judgement call. Download the file again.")
            if (decision != null) {
                Row { TextButton(onClick = { onWithdraw(decision.id) }, enabled = !busy) { Text("Undo") } }
            }
        }
    }
}
