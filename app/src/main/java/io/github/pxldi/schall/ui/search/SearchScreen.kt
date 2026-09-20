package io.github.pxldi.schall.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.ArtistSearchResult
import io.github.pxldi.schall.ui.common.Centered
import io.github.pxldi.schall.ui.common.Controls
import io.github.pxldi.schall.ui.common.ErrorLine
import io.github.pxldi.schall.ui.common.ListRow
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.Meta
import io.github.pxldi.schall.ui.common.Note
import io.github.pxldi.schall.ui.common.rememberAction

/** Artist search against MusicBrainz, through the server. The query runs on
 * submit, not on every keystroke: MusicBrainz is rate limited and a search
 * per letter would spend the whole allowance on half-typed names. */
@Composable
fun SearchScreen() {
    var typed by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Artist name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { query = typed.trim() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { query = typed.trim() }, enabled = typed.isNotBlank()) { Text("Search") }
        }
        if (query.isNotEmpty()) Results(query)
    }
}

@Composable
private fun Results(query: String) {
    val api = LocalApi.current
    val loader = viewModel(key = "search:$query") { Loader { api.searchArtists(query).items } }
    val load by loader.state.collectAsStateWithLifecycle()
    when (val state = load) {
        Load.Loading -> Centered { CircularProgressIndicator() }
        is Load.Failed -> Note(state.message, error = true)
        is Load.Ready -> LazyColumn(Modifier.fillMaxSize()) {
            if (state.value.isEmpty()) item { Note("MusicBrainz knows no artist by that name.") }
            items(state.value.size, key = { state.value[it].musicbrainzId }) { ArtistRow(state.value[it]) }
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistSearchResult) {
    val api = LocalApi.current
    val follow = rememberAction()
    var following by rememberSaveable(artist.musicbrainzId) { mutableStateOf(false) }
    val detail = listOfNotNull(artist.type, artist.area ?: artist.country, artist.disambiguation).joinToString(" · ")

    ListRow(artist.name, detail) {
        ErrorLine(follow.error)
        Controls {
            if (following) {
                Meta("Following")
            } else {
                Button(
                    onClick = { follow.run(done = { following = true }) { api.followArtist(artist) } },
                    enabled = !follow.busy,
                ) { Text("Follow") }
            }
        }
    }
}
