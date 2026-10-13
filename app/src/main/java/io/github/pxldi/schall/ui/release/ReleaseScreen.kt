package io.github.pxldi.schall.ui.release

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.ReleaseDetail
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.data.Track
import io.github.pxldi.schall.domain.formatDuration
import io.github.pxldi.schall.ui.common.CoverRef
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.PrimaryButton
import io.github.pxldi.schall.ui.common.PrintMode
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.Rule
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.StateMark
import io.github.pxldi.schall.ui.common.Tone
import io.github.pxldi.schall.ui.common.WantToggle
import io.github.pxldi.schall.ui.common.rememberAction
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import io.github.pxldi.schall.ui.theme.Type

/** A song the library holds, here or on another release. */
internal fun Track.held() = owned || heldElsewhere

/** Wanted and not yet held: what the tick on a row shows. */
internal fun Track.wanted() = wantStatus != null && wantStatus != "not_wanted" && wantStatus != "acquired"

internal fun kind(albumType: String) = albumType.replaceFirstChar { it.uppercase() }

/** One release: the cover printed large, the facts, and the tracklist with
 * a want control on every song the library does not hold. Editions, sources
 * and the discography stay on the web, so the phone page stays one list. */
@Composable
fun ReleaseScreen(id: String, back: () -> Unit, openArtist: (String) -> Unit) {
    val api = LocalApi.current
    val store = LocalAppStore.current
    val detail = viewModel(store, key = "release:$id") { Loader { api.release(id) } }
    val tracks = viewModel(store, key = "release-tracks:$id") { Loader { api.tracks(id).items } }
    val reload = {
        detail.refresh()
        tracks.refresh()
    }
    KeepFresh(Stale.Wants, Stale.Downloads) { reload() }

    val detailLoad by detail.state.collectAsStateWithLifecycle()
    val tracksLoad by tracks.state.collectAsStateWithLifecycle()
    val refreshing by tracks.refreshing.collectAsStateWithLifecycle()
    val release = (detailLoad as? Load.Ready<ReleaseDetail>)?.value
    val items = (tracksLoad as? Load.Ready<List<Track>>)?.value.orEmpty()
    val act = rememberAction()
    var menu by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }

    val held = items.count { it.held() }
    val wanted = items.count { !it.held() && it.wanted() }
    val missing = items.count { !it.held() && !it.wanted() && it.musicbrainzRecordingId != null }
    val facts = listOfNotNull(
        release?.albumType?.takeIf { it.isNotEmpty() }?.let(::kind),
        release?.firstReleaseDate?.take(4),
        if (items.isNotEmpty()) "$held of ${items.size}" else null,
    ).joinToString(" · ")

    PrintedPage(
        title = release?.title.orEmpty(),
        covers = listOf(CoverRef(api.coverUrl(id), id, release?.inks?.toInks())),
        mode = PrintMode.Single,
        printHeight = 340.dp,
        titleSize = 56,
        back = back,
        refreshing = refreshing,
        onRefresh = reload,
        actions = {
            if (held > 0 || wanted > 0) {
                Box {
                    IconButton(onClick = { menu = true }) { MisprintIcon(Misprint.More, description = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove", style = Type.body) },
                            leadingIcon = { MisprintIcon(Misprint.Delete, size = 18.dp) },
                            onClick = {
                                menu = false
                                removing = true
                            },
                        )
                    }
                }
            }
        },
        belowTitle = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    if (release != null) {
                        Text(release.artistName, style = Type.name, color = Duoton.ink, modifier = Modifier.clickable { openArtist(release.artistId) })
                    }
                    if (facts.isNotEmpty()) Text(facts, style = Type.body, color = Duoton.ink2)
                }
                if (missing > 0) {
                    PrimaryButton("Want rest", Misprint.Plus, { act.run(Stale.Wants) { api.wantRelease(id); reload() } }, enabled = !act.busy, height = 40.dp)
                }
            }
        },
    ) {
        if (act.error.isNotEmpty()) item { Said(act.error, error = true) }
        (detailLoad as? Load.Failed)?.let { item { Said(it.message, error = true) } }
        (tracksLoad as? Load.Failed)?.let { item { Said(it.message, error = true) } }
        if (tracksLoad is Load.Ready && items.isEmpty()) {
            item {
                Said(
                    when (release?.trackRefreshStatus) {
                        "queued", "running" -> "Importing the track list…"
                        "failed" -> "Reading the track list from MusicBrainz failed."
                        else -> "No track list."
                    },
                )
            }
        }
        val discs = (release?.mediaCount ?: 1) > 1
        items.forEach { track ->
            item(key = track.id) {
                TrackRow(track, discs) { reload() }
                Rule()
            }
        }
    }

    if (removing && release != null) {
        AlertDialog(
            onDismissRequest = { removing = false },
            containerColor = Duoton.panel,
            title = { Text("Remove ${release.title}?", style = Type.name) },
            text = { Text("Stops wanting it. What Schall downloaded is deleted in 30 days.", style = Type.body, color = Duoton.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    removing = false
                    act.run(Stale.Wants) { api.removeRelease(id); reload() }
                }) { Text("Remove", color = Duoton.fail) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep", color = Duoton.ink) } },
        )
    }
}

@Composable
private fun TrackRow(track: Track, discs: Boolean, changed: () -> Unit) {
    val api = LocalApi.current
    val act = rememberAction()
    val held = track.held()
    val lost = !held && track.wantStatus == "acquired"
    val number = listOfNotNull(if (discs) track.discNumber.toString() else null, track.trackNumber?.toString()).joinToString(".")
    val line = listOfNotNull(
        formatDuration(track.durationMs).ifEmpty { null },
        track.removesAt?.let { "deleted on ${it.take(10)}" },
        if (track.wantStatus == "awaiting_review") "to review" else null,
        if (lost) "file gone" else null,
    ).joinToString(" · ")

    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number, style = Type.body, color = Duoton.ink3, textAlign = TextAlign.End, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = Type.name, color = if (held) Duoton.ink else Duoton.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.isNotEmpty()) Text(line, style = Type.small, color = if (track.removesAt != null || lost) Duoton.fail else Duoton.ink3)
            if (act.error.isNotEmpty()) Text(act.error, style = Type.small, color = Duoton.fail)
        }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            when {
                held && track.removesAt == null -> StateMark(Misprint.Check, Tone.Own, "In your library")
                track.wantStatus == "awaiting_review" -> StateMark(Misprint.Checklist, Tone.Want, "Review")
                lost -> StateMark(Misprint.Alert, Tone.Fail, "Fetched once, file gone")
                track.musicbrainzRecordingId != null -> {
                    val wanted = !held && track.wanted()
                    WantToggle(wanted, track.title, {
                        act.run(Stale.Wants) {
                            if (wanted) api.removeTrack(track.id) else api.wantTrack(track.id)
                            changed()
                        }
                    }, enabled = !act.busy)
                }
            }
        }
    }
}
