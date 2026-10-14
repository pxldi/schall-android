@file:OptIn(ExperimentalLayoutApi::class)

package io.github.pxldi.schall.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pxldi.schall.data.AcquisitionCandidate
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.ImportDecision
import io.github.pxldi.schall.data.ImportFileEvidence
import io.github.pxldi.schall.data.ImportTags
import io.github.pxldi.schall.data.ReviewItem
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.CopyGroupView
import io.github.pxldi.schall.domain.Question
import io.github.pxldi.schall.domain.formatBytes
import io.github.pxldi.schall.domain.formatDuration
import io.github.pxldi.schall.domain.groupCopies
import io.github.pxldi.schall.domain.importCatalogue
import io.github.pxldi.schall.domain.orderFiles
import io.github.pxldi.schall.domain.questions
import io.github.pxldi.schall.domain.suggestedTrack
import io.github.pxldi.schall.domain.trackLabel
import io.github.pxldi.schall.ui.common.Action
import io.github.pxldi.schall.ui.common.Chip
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.PrimaryButton
import io.github.pxldi.schall.ui.common.PrintMode
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.QuietButton
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.SectionLabel
import io.github.pxldi.schall.ui.common.Tone
import io.github.pxldi.schall.ui.common.rememberAction
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.LocalInks
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import io.github.pxldi.schall.ui.theme.Type

/** One question, its evidence, and the same answers the web offers in the
 * same words. Answering or skipping opens the next question in the list, so a
 * queue can be worked through without going back to it. */
@Composable
fun ReviewQuestionScreen(id: String, back: () -> Unit, openInstead: (String) -> Unit) {
    val api = LocalApi.current
    val (queue, folders) = reviewLoaders()
    KeepFresh(Stale.ReviewQueue, Stale.Downloads) {
        queue.refresh()
        folders.refresh()
    }
    val queueLoad by queue.state.collectAsStateWithLifecycle()
    val foldersLoad by folders.state.collectAsStateWithLifecycle()
    val loading = queueLoad is Load.Loading || foldersLoad is Load.Loading
    val all = questions((queueLoad as? Load.Ready)?.value, (foldersLoad as? Load.Ready)?.value)
    val index = all.indexOfFirst { it.id == id }
    val question = all.getOrNull(index)
    val decide = rememberAction()

    if (question == null) {
        Box(Modifier.fillMaxSize().background(Duoton.ground).padding(24.dp), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(color = Duoton.ink2)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("This was already decided.", style = Type.body, color = Duoton.ink2)
                QuietButton("Back", Misprint.ChevronLeft, back)
            }
        }
        return
    }

    val next = all.getOrNull(index + 1) ?: all.getOrNull(index - 1)
    val onward: () -> Unit = { if (next != null) openInstead(next.id) else back() }
    val face = face(question, api)
    val counter = "${index + 1} of ${all.size}"

    when (question) {
        is Question.Folder -> FolderQuestion(question.download, face, counter, decide, back, onward)
        is Question.Downloaded -> WantQuestion(question.want, version = false, face, counter, decide, back, onward, skip = next?.let { { openInstead(it.id) } })
        is Question.Version -> WantQuestion(question.want, version = true, face, counter, decide, back, onward, skip = next?.let { { openInstead(it.id) } })
    }
}

@Composable
private fun Counter(text: String) {
    Text(text, style = Type.body, color = Duoton.ink, modifier = Modifier.padding(end = 16.dp))
}

/** The answers, fixed at the bottom so the thumb finds them on every question. */
@Composable
private fun AnswerBar(error: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Duoton.ground).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)) {
        if (error.isNotEmpty()) Text(error, style = Type.small, color = Duoton.fail, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun WantQuestion(
    want: ReviewItem,
    version: Boolean,
    face: QuestionFace,
    counter: String,
    decide: Action,
    back: () -> Unit,
    onward: () -> Unit,
    skip: (() -> Unit)?,
) {
    val api = LocalApi.current
    val target = want.target
    val filed = want.kind == "stopped" && want.fileRecordingId != null
    val groups = remember(want) {
        groupCopies(want.copies.filter { it.verdict == "held" || want.kind == "stopped" }, want.copyGroups)
    }
    val preview = rememberPreview()
    var chosen by rememberSaveable(want.target.id) { mutableStateOf<String?>(null) }
    var removing by rememberSaveable(want.target.id) { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    fun answered(block: suspend () -> Unit) = decide.run(Stale.ReviewQueue, Stale.Downloads, Stale.Wants, done = onward, block = block)

    Column(Modifier.fillMaxSize().background(Duoton.ground)) {
        Box(Modifier.weight(1f)) {
            PrintedPage(
                title = target.title,
                covers = listOf(face.cover),
                mode = PrintMode.Single,
                printHeight = 340.dp,
                titleSize = 56,
                subtitle = listOf(target.artist, target.album, formatDuration(target.durationMs)).filter { it.isNotEmpty() }.joinToString(" · "),
                back = back,
                actions = { Counter(counter) },
            ) {
                if (!version && !filed) {
                    if (groups.isEmpty()) item { Said("No copies left.") }
                    groups.forEach { group ->
                        item(key = group.best.id) {
                            CopyCard(group, chosen = chosen == group.best.id, preview = preview) { chosen = group.best.id }
                        }
                    }
                }
                if (!version && filed) {
                    item(key = "filed") {
                        Panel(selected = false) {
                            Text("In your library", style = Type.label, color = Duoton.ink3)
                            Text(want.fileTitle.orEmpty(), style = Type.name, color = Duoton.ink)
                            Text(listOfNotNull(want.fileArtist, want.fileReleaseTitle).joinToString(" · "), style = Type.body, color = Duoton.ink2)
                            Text("Filed as another song. Accept to match it to this one.", style = Type.small, color = Duoton.ink2)
                        }
                    }
                }
                if (version) {
                    want.candidates.forEach { candidate ->
                        item(key = candidate.recordingId) {
                            CandidateCard(candidate, chosen = chosen == candidate.recordingId) { chosen = candidate.recordingId }
                        }
                    }
                }
                if (want.ruledOut > 0) item { Said("${want.ruledOut} ruled out already.") }
            }
        }

        AnswerBar(decide.error) {
            val enabled = !decide.busy
            when {
                version -> PrimaryButton("Use version", Misprint.Check, { chosen?.let { recording -> answered { api.chooseRecording(target.id, recording) } } }, Modifier.weight(1f), enabled = enabled && chosen != null)
                filed -> PrimaryButton("Accept copy", Misprint.Check, { answered { api.acceptFiledCopy(target.id) } }, Modifier.weight(1f), enabled = enabled)
                else -> PrimaryButton("Accept copy", Misprint.Check, { chosen?.let { copy -> answered { api.acceptCopy(copy) } } }, Modifier.weight(1f), enabled = enabled && chosen != null)
            }
            if (!version && !filed) {
                QuietButton(null, Misprint.Close, { answered { api.noneOfThese(target.id) } }, enabled = enabled, description = "None of these")
            }
            if (skip != null) QuietButton(null, Misprint.Last, skip, enabled = enabled, description = "Skip")
            Box {
                QuietButton(null, Misprint.More, { more = true }, enabled = enabled, description = "More answers")
                DropdownMenu(expanded = more, onDismissRequest = { more = false }, containerColor = Duoton.raise) {
                    if (!version) {
                        DropdownMenuItem(
                            text = { Text("Wrong song", style = Type.name) },
                            leadingIcon = { MisprintIcon(Misprint.Ban, size = 18.dp) },
                            onClick = {
                                more = false
                                answered { api.wrongSong(target.id) }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from wishlist", style = Type.name) },
                        leadingIcon = { MisprintIcon(Misprint.Delete, size = 18.dp) },
                        onClick = {
                            more = false
                            removing = true
                        },
                    )
                }
            }
        }
    }

    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            containerColor = Duoton.raise,
            title = { Text("Remove from wishlist?", style = Type.name) },
            text = { Text("Schall stops searching for ${target.artist} – ${target.title}.", style = Type.body, color = Duoton.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    removing = false
                    answered { api.stopLooking(target.id) }
                }) { Text("Remove", color = Duoton.ink) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep", color = Duoton.ink2) } },
        )
    }
}

/** A card on the panel; the chosen one is ringed in the page's light ink. */
@Composable
private fun Panel(selected: Boolean, onSelect: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val inks = LocalInks.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(Duoton.panel)
            .border(2.dp, if (selected) inks.light else Color.Transparent, shape)
            .then(if (onSelect != null) Modifier.selectable(selected = selected, onClick = onSelect, role = Role.RadioButton) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Evidence(agrees: List<String>, differs: List<String>) {
    if (agrees.isEmpty() && differs.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        agrees.forEach { Chip(it, Tone.Quiet, Misprint.Check) }
        differs.forEach { Chip(it, Tone.Want, Misprint.Alert) }
    }
}

@Composable
private fun CopyCard(group: CopyGroupView, chosen: Boolean, preview: Preview, onChoose: () -> Unit) {
    val copy = group.best
    val evidence = copy.evidence
    val current = preview.current == copy.id
    val format = copy.name.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 }
    Panel(selected = chosen, onSelect = onChoose) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(current && preview.playing, onClick = {
                onChoose()
                preview.toggle(copy.id)
            })
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(format, evidence?.bitRate?.let { "$it kbps" }, evidence?.observed?.durationMs?.let { formatDuration(it) }).joinToString(" · ").ifEmpty { copy.name },
                    style = Type.name,
                    color = Duoton.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        copy.username.ifEmpty { null },
                        formatBytes(copy.sizeBytes ?: evidence?.sizeBytes).ifEmpty { null },
                        if (group.rips > 1) "${group.rips} rips" else null,
                    ).joinToString(" · "),
                    style = Type.body,
                    color = Duoton.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (current) Text(formatDuration(preview.position).ifEmpty { "0:00" }, style = Type.small, color = Duoton.ink3)
        }
        if (current) Scrubber(preview)
        Text(copy.name, style = Type.small, color = Duoton.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Evidence(evidence?.agrees.orEmpty(), evidence?.differs.orEmpty())
        if (copy.summary.isNotEmpty()) Text(copy.summary, style = Type.small, color = Duoton.ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CandidateCard(candidate: AcquisitionCandidate, chosen: Boolean, onChoose: () -> Unit) {
    Panel(selected = chosen, onSelect = onChoose) {
        Text(candidate.trackTitle, style = Type.name, color = Duoton.ink)
        Text(listOfNotNull(candidate.artistName.ifEmpty { null }, candidate.releaseTitle).joinToString(" · "), style = Type.body, color = Duoton.ink2)
        val details = listOfNotNull(formatDuration(candidate.durationMs).ifEmpty { null }, candidate.isrc).joinToString(" · ")
        if (details.isNotEmpty()) Text(details, style = Type.small, color = Duoton.ink3)
        Evidence(candidate.agrees, candidate.differs)
    }
}

/** An album folder whose files did not all match. Each file that stopped the
 * import can be named as one track of the release; that settles the file
 * without leaving the folder, because there is usually another file left, so
 * it refetches instead of moving on. Check again runs the import with the
 * decisions applied. */
@Composable
private fun FolderQuestion(download: DownloadRequest, face: QuestionFace, counter: String, decide: Action, back: () -> Unit, onward: () -> Unit) {
    val api = LocalApi.current
    val settle = rememberAction()
    val evidence = download.importEvidence
    val catalogue = remember(evidence) { importCatalogue(evidence) }
    val files = remember(evidence) { orderFiles(evidence?.files.orEmpty()) }
    val missing = evidence?.unmatchedTracks.orEmpty()

    Column(Modifier.fillMaxSize().background(Duoton.ground)) {
        Box(Modifier.weight(1f)) {
            PrintedPage(
                title = face.title,
                covers = listOf(face.cover),
                mode = PrintMode.Single,
                printHeight = 300.dp,
                titleSize = 52,
                subtitle = listOf(face.subtitle, download.username).filter { it.isNotEmpty() }.joinToString(" · "),
                back = back,
                actions = { Counter(counter) },
            ) {
                download.importError?.takeIf { it.isNotEmpty() }?.let { item { Said(it, error = true) } }
                evidence?.problems?.forEach { problem -> item { Said(problem, error = true) } }
                files.forEach { file ->
                    item(key = file.name) {
                        FileCard(
                            file = file,
                            catalogue = catalogue,
                            decision = download.importDecisions.firstOrNull { it.fileName == file.name },
                            busy = settle.busy,
                            onResolve = { trackId -> settle.run(Stale.Downloads) { api.resolveImportTrack(download.id, file.name, trackId) } },
                            onWithdraw = { decisionId -> settle.run(Stale.Downloads) { api.withdrawImportResolution(download.id, decisionId) } },
                        )
                    }
                }
                if (missing.isNotEmpty()) item { Said("No file for ${missing.joinToString(", ") { trackLabel(it) }}.") }
            }
        }
        AnswerBar(settle.error.ifEmpty { decide.error }) {
            PrimaryButton(
                "Check again",
                Misprint.Refresh,
                { decide.run(Stale.Downloads, Stale.ReviewQueue, done = onward) { api.revalidateDownload(download.id) } },
                Modifier.weight(1f),
                enabled = !decide.busy,
            )
        }
    }
}

/** One file of the folder. A file that matched says how; one that did not
 * lists the tracks it could be, the evidence's own candidates first and the
 * rest of the release behind All tracks. */
@Composable
private fun FileCard(
    file: ImportFileEvidence,
    catalogue: List<ImportTags>,
    decision: ImportDecision?,
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
    val inks = LocalInks.current

    Panel(selected = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MisprintIcon(
                if (open) Misprint.Alert else Misprint.Check,
                size = 16.dp,
                tint = if (open) Duoton.want else Duoton.own,
            )
            Text(file.name, style = Type.name.copy(fontSize = Type.body.fontSize), color = Duoton.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        when {
            decision != null -> Text("Resolved as “${decision.trackTitle}”. Check again to apply.", style = Type.small, color = Duoton.ink2)
            file.match != null -> Text(file.match.summary, style = Type.small, color = Duoton.ink2)
        }
        if (open) file.problems.forEach { Text(it, style = Type.small, color = Duoton.want) }
        if (open && file.resolvable && catalogue.isNotEmpty()) {
            SectionLabel("This file is", Modifier.padding(start = 0.dp))
            shown.forEach { track ->
                val candidate = file.candidates?.firstOrNull { it.track.trackId == track.trackId }
                val checked = selected == track.trackId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .selectable(selected = checked, onClick = { chosen = track.trackId.orEmpty() }, role = Role.RadioButton)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (checked) inks.light else Duoton.line2, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (checked) Box(Modifier.size(10.dp).clip(CircleShape).background(inks.light)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(trackLabel(track), style = Type.body, color = Duoton.ink)
                        Evidence(candidate?.agrees.orEmpty(), candidate?.differs.orEmpty())
                        candidate?.takenBy?.let { Text("Already matched by $it", style = Type.small, color = Duoton.ink3) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Resolve", Misprint.Check, { onResolve(selected) }, enabled = !busy && selected.isNotEmpty(), height = 40.dp)
                if (!all && shown.size < catalogue.size) QuietButton("All tracks", null, { all = true }, height = 40.dp)
            }
        }
        if (open && !file.resolvable) Text("Can't fix by hand. Download it again.", style = Type.small, color = Duoton.ink2)
        if (decision != null) QuietButton("Undo", Misprint.Undo, { onWithdraw(decision.id) }, enabled = !busy, height = 40.dp)
    }
}
