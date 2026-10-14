package io.github.pxldi.schall.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.ArtistSearchResult
import io.github.pxldi.schall.data.FoundTrack
import io.github.pxldi.schall.data.GlobalSearch
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.formatDuration
import io.github.pxldi.schall.ui.common.CoverArt
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.QuietButton
import io.github.pxldi.schall.ui.common.Row2
import io.github.pxldi.schall.ui.common.Rule
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.SectionLabel
import io.github.pxldi.schall.ui.common.StateMark
import io.github.pxldi.schall.ui.common.Tone
import io.github.pxldi.schall.ui.common.WantToggle
import io.github.pxldi.schall.ui.common.rememberAction
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import io.github.pxldi.schall.ui.theme.Type
import kotlinx.coroutines.delay

/** The library search: artists, releases and songs, as the web's search
 * popup finds them, asked 250 ms after the last keystroke. An artist the
 * library does not know yet is found on MusicBrainz, on request, because
 * MusicBrainz is rate limited and a search per letter would spend it. */
@Composable
fun SearchScreen(openArtist: (String) -> Unit, openRelease: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf("") }
    var musicbrainz by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(typed) {
        delay(250)
        asked = typed.trim()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(52.dp)
                .background(Duoton.raise.copy(alpha = 0.78f), RoundedCornerShape(26.dp))
                .border(1.dp, Duoton.line2, RoundedCornerShape(26.dp))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MisprintIcon(Misprint.Search, size = 20.dp, tint = Duoton.ink2)
            Box(Modifier.weight(1f)) {
                if (typed.isEmpty()) Text("Search Schall", style = Type.name.copy(fontSize = 17.sp), color = Duoton.ink3)
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = Type.name.copy(fontSize = 17.sp, color = Duoton.ink),
                    cursorBrush = SolidColor(Duoton.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { asked = typed.trim() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search" },
                )
            }
            if (typed.isNotEmpty()) {
                IconButton(onClick = {
                    typed = ""
                    musicbrainz = ""
                }) { MisprintIcon(Misprint.Close, size = 18.dp, description = "Clear") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (asked.isEmpty()) item { Said("Artists, releases and songs in your library.") }
            else {
                library(asked, openArtist, openRelease)
                if (musicbrainz == asked) musicBrainz(asked)
                else item(key = "ask-musicbrainz") {
                    Row2(
                        title = "Find “$asked” on MusicBrainz",
                        subtitle = "To follow an artist that is not here yet",
                        onClick = { musicbrainz = asked },
                        leading = { Box(Modifier.padding(horizontal = 12.dp)) { MisprintIcon(Misprint.External, tint = Duoton.ink2) } },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.library(query: String, openArtist: (String) -> Unit, openRelease: (String) -> Unit) {
    item(key = "library:$query") {
        val api = LocalApi.current
        val loader = viewModel(key = "search:$query") { Loader { api.search(query) } }
        val load by loader.state.collectAsStateWithLifecycle()
        when (val state = load) {
            Load.Loading -> Said("Searching…")
            is Load.Failed -> Said(state.message, error = true)
            is Load.Ready -> Found(state.value, query, openArtist, openRelease) { loader.refresh() }
        }
    }
}

@Composable
private fun Found(found: GlobalSearch, query: String, openArtist: (String) -> Unit, openRelease: (String) -> Unit, changed: () -> Unit) {
    val api = LocalApi.current
    if (found.artists.isEmpty() && found.releases.isEmpty() && found.tracks.isEmpty()) {
        Said("Nothing matches “$query” in your library.")
        return
    }
    Column {
        if (found.artists.isNotEmpty()) {
            SectionLabel("Artists")
            found.artists.forEach { artist ->
                Row2(
                    title = artist.name,
                    subtitle = listOfNotNull(
                        if (artist.countedReleases > 0) "${artist.ownedReleases} of ${artist.countedReleases} releases" else null,
                        if (artist.followed) "following" else null,
                    ).joinToString(" · "),
                    onClick = { openArtist(artist.id) },
                    leading = { CoverArt(api.artistImageUrl(artist.id), artist.id, shape = RoundedCornerShape(22.dp)) },
                )
            }
        }
        if (found.releases.isNotEmpty()) {
            SectionLabel("Releases")
            found.releases.forEach { release ->
                Row2(
                    title = release.title,
                    subtitle = listOfNotNull(
                        release.artistName.ifEmpty { null },
                        release.releaseDate?.take(4),
                        if (release.trackCount > 0) "${release.ownedTrackCount} of ${release.trackCount}" else null,
                    ).joinToString(" · "),
                    onClick = { openRelease(release.id) },
                    leading = { CoverArt(api.coverUrl(release.id), release.id, release.inks?.toInks()) },
                )
            }
        }
        if (found.tracks.isNotEmpty()) {
            SectionLabel("Songs")
            found.tracks.forEach { track -> SongRow(track, openRelease, changed) }
        }
    }
}

@Composable
private fun SongRow(track: FoundTrack, openRelease: (String) -> Unit, changed: () -> Unit) {
    val api = LocalApi.current
    val act = rememberAction()
    val held = track.owned || track.wantStatus == "acquired"
    val wanted = !held && track.wantStatus != null && track.wantStatus != "not_wanted"
    Row2(
        title = track.title,
        subtitle = listOf(track.artistName, formatDuration(track.durationMs)).filter { it.isNotEmpty() }.joinToString(" · "),
        onClick = { openRelease(track.albumId) },
        leading = { CoverArt(api.coverUrl(track.albumId), track.albumId) },
    ) {
        if (held) StateMark(Misprint.Check, Tone.Own, "In your library")
        else WantToggle(wanted, track.title, { act.run(Stale.Wants) { if (wanted) api.removeTrack(track.id) else api.wantTrack(track.id); changed() } }, enabled = !act.busy)
    }
    if (act.error.isNotEmpty()) Said(act.error, error = true)
}

private fun LazyListScope.musicBrainz(query: String) {
    item(key = "musicbrainz:$query") {
        val api = LocalApi.current
        val loader = viewModel(key = "musicbrainz:$query") { Loader { api.searchArtists(query).items } }
        val load by loader.state.collectAsStateWithLifecycle()
        Column {
            SectionLabel("MusicBrainz")
            when (val state = load) {
                Load.Loading -> Said("Asking MusicBrainz…")
                is Load.Failed -> Said(state.message, error = true)
                is Load.Ready -> {
                    if (state.value.isEmpty()) Said("No artist by that name on MusicBrainz.")
                    state.value.forEach { NewArtistRow(it) }
                }
            }
        }
    }
}

@Composable
private fun NewArtistRow(artist: ArtistSearchResult) {
    val api = LocalApi.current
    val follow = rememberAction()
    var following by rememberSaveable(artist.musicbrainzId) { mutableStateOf(false) }
    Row2(
        title = artist.name,
        subtitle = listOfNotNull(artist.type, artist.area ?: artist.country, artist.disambiguation).joinToString(" · "),
    ) {
        if (following) StateMark(Misprint.Check, Tone.Own, "Following")
        else QuietButton(null, Misprint.Follow, { follow.run(done = { following = true }) { api.followArtist(artist) } }, enabled = !follow.busy, description = "Follow", height = 40.dp)
    }
    if (follow.error.isNotEmpty()) Said(follow.error, error = true)
    Rule()
}
