package io.github.pxldi.schall.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.ArtistDetail
import io.github.pxldi.schall.data.Release
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.ui.common.Choice
import io.github.pxldi.schall.ui.common.Choices
import io.github.pxldi.schall.ui.common.CoverArt
import io.github.pxldi.schall.ui.common.CoverRef
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.PrimaryButton
import io.github.pxldi.schall.ui.common.PrintMode
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.QuietButton
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.rememberAction
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import io.github.pxldi.schall.ui.theme.Type

/** The web's discography sections, in its order. */
private enum class Section(val label: String) { Albums("Albums"), EPs("EPs"), Singles("Singles"), Other("Other") }

private fun Release.section() = when {
    albumType == "album" && secondaryTypes.isEmpty() -> Section.Albums
    albumType == "ep" -> Section.EPs
    albumType == "single" -> Section.Singles
    else -> Section.Other
}

/** The songs that count towards complete, as the web reckons them. */
private fun Release.countable() = if (artistMonitorLevel == "owned") ownedTrackCount else trackCount - dismissedTrackCount

@Composable
fun ArtistScreen(id: String, back: () -> Unit, openRelease: (String) -> Unit) {
    val api = LocalApi.current
    val store = LocalAppStore.current
    val detail = viewModel(store, key = "artist:$id") { Loader { api.artist(id) } }
    val releases = viewModel(store, key = "artist-releases:$id") { Loader { api.releasesOf(id).items } }
    val reload = {
        detail.refresh()
        releases.refresh()
    }
    KeepFresh(Stale.Wants) { reload() }

    val detailLoad by detail.state.collectAsStateWithLifecycle()
    val releasesLoad by releases.state.collectAsStateWithLifecycle()
    val refreshing by releases.refreshing.collectAsStateWithLifecycle()
    val artist = (detailLoad as? Load.Ready<ArtistDetail>)?.value
    val all = (releasesLoad as? Load.Ready<List<Release>>)?.value.orEmpty()
    val bySection = all.groupBy { it.section() }
    var section by rememberSaveable { mutableStateOf(Section.Albums) }
    val sections = Section.entries.filter { bySection[it].orEmpty().isNotEmpty() }
    val shown = (bySection[section] ?: bySection[sections.firstOrNull()]).orEmpty()
        .sortedWith(compareByDescending<Release> { it.firstReleaseDate.orEmpty() }.thenBy { it.title })
    val act = rememberAction()
    var menu by rememberSaveable { mutableStateOf(false) }
    var unfollowing by rememberSaveable { mutableStateOf(false) }

    val lead = artist?.leadAlbumId
    val summary = listOfNotNull(
        artist?.discography?.takeIf { it.counted > 0 }?.let { "${it.owned} of ${it.counted} releases" },
        artist?.genres?.take(2)?.joinToString(", ")?.ifEmpty { null },
    ).joinToString(" · ")

    PrintedPage(
        title = artist?.name.orEmpty(),
        covers = listOf(if (lead != null) CoverRef(api.coverUrl(lead), lead, artist.inks?.toInks()) else CoverRef(null, id, artist?.inks?.toInks())),
        mode = PrintMode.Single,
        printHeight = 300.dp,
        titleSize = 56,
        back = back,
        refreshing = refreshing,
        onRefresh = reload,
        actions = {
            if (artist != null && (artist.wantMissing || artist.discography.missing > 0)) {
                Box {
                    IconButton(onClick = { menu = true }) { MisprintIcon(Misprint.More, description = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (artist.wantMissing) "Remove missing" else "Add ${artist.discography.missing} missing", style = Type.body) },
                            leadingIcon = { MisprintIcon(if (artist.wantMissing) Misprint.Minus else Misprint.Want, size = 18.dp) },
                            onClick = {
                                menu = false
                                act.run(Stale.Wants) { api.wantMissing(id, !artist.wantMissing); reload() }
                            },
                        )
                    }
                }
            }
        },
        belowTitle = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(summary, style = Type.body, color = Duoton.ink2, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (artist != null) {
                    if (artist.followed) QuietButton("Following", Misprint.Check, { unfollowing = true }, enabled = !act.busy, height = 40.dp)
                    else PrimaryButton("Follow", Misprint.Follow, { act.run { api.follow(id); reload() } }, enabled = !act.busy, height = 40.dp)
                }
            }
        },
    ) {
        if (act.error.isNotEmpty()) item { Said(act.error, error = true) }
        (detailLoad as? Load.Failed)?.let { item { Said(it.message, error = true) } }
        (releasesLoad as? Load.Failed)?.let { item { Said(it.message, error = true) } }
        if (sections.size > 1) {
            item(key = "sections") {
                Choices {
                    sections.forEach { entry ->
                        Choice(entry.label, entry == section || (section !in sections && entry == sections.first()), { section = entry })
                    }
                }
            }
        }
        if (releasesLoad is Load.Ready && all.isEmpty()) item { Said("No releases yet.") }
        shown.chunked(2).forEachIndexed { index, pair ->
            item(key = "row-$index-${pair.first().id}") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { release -> Tile(release, Modifier.weight(1f)) { openRelease(release.id) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (unfollowing && artist != null) {
        AlertDialog(
            onDismissRequest = { unfollowing = false },
            containerColor = Duoton.panel,
            title = { Text("Unfollow ${artist.name}?", style = Type.name) },
            text = { Text("Schall stops watching for their new releases. Your files stay.", style = Type.body, color = Duoton.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    unfollowing = false
                    act.run { api.unfollow(id); reload() }
                }) { Text("Unfollow", color = Duoton.fail) }
            },
            dismissButton = { TextButton(onClick = { unfollowing = false }) { Text("Keep following", color = Duoton.ink) } },
        )
    }
}

/** A release in the grid: the cover, faded while nothing of it is owned, the
 * title, and how much of it the library holds. */
@Composable
private fun Tile(release: Release, modifier: Modifier, open: () -> Unit) {
    val api = LocalApi.current
    val countable = release.countable().coerceAtLeast(0)
    val owned = release.ownedTrackCount
    val fraction = if (countable > 0) (owned.toFloat() / countable).coerceIn(0f, 1f) else 0f
    Column(modifier.clickable(onClick = open), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (owned == 0) 0.5f else 1f)) {
            CoverArt(if (release.hasCover) api.coverUrl(release.id) else null, release.id, release.inks?.toInks(), size = Dp.Unspecified, shape = RoundedCornerShape(8.dp))
        }
        Text(release.title, style = Type.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(
            listOfNotNull(release.firstReleaseDate?.take(4), if (countable > 0) "$owned of $countable" else null).joinToString(" · "),
            style = Type.small,
            color = Duoton.ink2,
        )
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Duoton.line)) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Duoton.own))
        }
    }
}
