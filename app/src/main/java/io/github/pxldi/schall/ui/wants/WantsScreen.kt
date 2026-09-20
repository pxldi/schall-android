package io.github.pxldi.schall.ui.wants

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.AcquisitionTarget
import io.github.pxldi.schall.data.AcquisitionTargets
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.formatDuration
import io.github.pxldi.schall.domain.relative
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

private enum class Pile { Looking, Stopped }

/** The wants: what is being looked for, and what somebody stopped looking
 * for, because a decision to stop is taken back where it was taken. */
@Composable
fun WantsScreen() {
    val api = LocalApi.current
    var pile by rememberSaveable { mutableStateOf(Pile.Looking) }
    val statuses = { which: Pile -> if (which == Pile.Looking) api.lookingFor else listOf("not_wanted") }
    val store = LocalAppStore.current
    val looking = viewModel(store, key = "wants:looking") { Loader { api.wants(statuses(Pile.Looking)) } }
    val stopped = viewModel(store, key = "wants:stopped") { Loader { api.wants(statuses(Pile.Stopped)) } }
    val loader = if (pile == Pile.Looking) looking else stopped
    KeepFresh(Stale.Wants) {
        looking.refresh()
        stopped.refresh()
    }

    val load by loader.state.collectAsStateWithLifecycle()
    val refreshing by loader.refreshing.collectAsStateWithLifecycle()
    val stale by loader.stale.collectAsStateWithLifecycle()
    val lookingLoad by looking.state.collectAsStateWithLifecycle()
    val stoppedLoad by stopped.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Piles(
            value = pile,
            options = listOf(
                Triple(Pile.Looking, "Looking", (lookingLoad as? Load.Ready<AcquisitionTargets>)?.value?.total),
                Triple(Pile.Stopped, "Stopped", (stoppedLoad as? Load.Ready<AcquisitionTargets>)?.value?.total),
            ),
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
            notice = (load as? Load.Ready)?.value?.notice,
        ) { want -> WantRow(want, pile) }
    }
}

@Composable
private fun WantRow(want: AcquisitionTarget, pile: Pile) {
    val api = LocalApi.current
    val act = rememberAction()
    val searching = want.status == "searching"
    val word = when {
        want.waitingOnYou -> "Waiting on you"
        pile == Pile.Stopped -> "Stopped"
        searching -> "Searching"
        else -> "Looking"
    }
    val detail = listOfNotNull(
        if (want.attempts > 0) "${want.attempts} tries" else "not tried yet",
        if (pile == Pile.Looking && want.nextAttemptAt != null) "next ${relative(want.nextAttemptAt)}" else null,
    ).joinToString(" · ")

    ListRow(want.title, listOf(want.artist, want.album, formatDuration(want.durationMs)).filter { it.isNotEmpty() }.joinToString(" · ")) {
        StateLine(word, detail)
        Meta(want.summary)
        Meta(want.lastError.orEmpty(), error = true)
        ErrorLine(act.error)
        Controls {
            if (pile == Pile.Looking) {
                OutlinedButton(onClick = { act.run(Stale.Wants) { api.stopLooking(want.id) } }, enabled = !act.busy) { Text("Stop looking") }
            } else {
                OutlinedButton(onClick = { act.run(Stale.Wants) { api.lookAgain(want.id) } }, enabled = !act.busy) { Text("Look again") }
            }
        }
    }
}
