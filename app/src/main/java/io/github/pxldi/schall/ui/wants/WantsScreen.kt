package io.github.pxldi.schall.ui.wants

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.AcquisitionTarget
import io.github.pxldi.schall.data.AcquisitionTargets
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.domain.relative
import io.github.pxldi.schall.ui.common.Choice
import io.github.pxldi.schall.ui.common.Choices
import io.github.pxldi.schall.ui.common.CoverArt
import io.github.pxldi.schall.ui.common.CoverRef
import io.github.pxldi.schall.ui.common.KeepFresh
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.QuietButton
import io.github.pxldi.schall.ui.common.Row2
import io.github.pxldi.schall.ui.common.Rule
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.StateMark
import io.github.pxldi.schall.ui.common.Tone
import io.github.pxldi.schall.ui.common.rememberAction
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.Type

/** The web's two piles: what is being looked for, and what somebody said they
 * don't want, because that decision is taken back where it was taken. */
private enum class Pile(val label: String) { Looking("Searching"), NotWanted("Removed") }

/** The mark at the end of a row, in the web's words and state colours. */
private fun standing(want: AcquisitionTarget): Triple<Misprint, Tone, String> = when {
    want.waitingOnYou -> Triple(Misprint.Pause, Tone.Want, "Needs you")
    want.status == "not_wanted" -> Triple(Misprint.Minus, Tone.Quiet, "Removed")
    want.status == "unresolved" -> Triple(Misprint.Busy, Tone.Busy, "Identifying")
    want.status == "searching" -> Triple(Misprint.Search, Tone.Busy, "Searching")
    else -> Triple(Misprint.Busy, Tone.Busy, "Looking for a copy")
}

@Composable
fun WantsScreen(back: () -> Unit) {
    val api = LocalApi.current
    var pile by rememberSaveable { mutableStateOf(Pile.Looking) }
    val store = LocalAppStore.current
    val looking = viewModel(store, key = "wants:looking") { Loader { api.wants(api.lookingFor) } }
    val notWanted = viewModel(store, key = "wants:stopped") { Loader { api.wants(listOf("not_wanted")) } }
    val loader = if (pile == Pile.Looking) looking else notWanted
    KeepFresh(Stale.Wants) {
        looking.refresh()
        notWanted.refresh()
    }

    val load by loader.state.collectAsStateWithLifecycle()
    val refreshing by loader.refreshing.collectAsStateWithLifecycle()
    val stale by loader.stale.collectAsStateWithLifecycle()
    val lookingLoad by looking.state.collectAsStateWithLifecycle()
    val notWantedLoad by notWanted.state.collectAsStateWithLifecycle()
    val page = (load as? Load.Ready<AcquisitionTargets>)?.value
    val items = page?.items.orEmpty()

    PrintedPage(
        title = "Wishlist",
        covers = items.mapNotNull { it.originAlbumId }.distinct().take(3).map { CoverRef(api.coverUrl(it), it) },
        back = back,
        refreshing = refreshing,
        onRefresh = loader::refresh,
    ) {
        item(key = "piles") {
            Choices {
                Pile.entries.forEach { entry ->
                    val total = ((if (entry == Pile.Looking) lookingLoad else notWantedLoad) as? Load.Ready<AcquisitionTargets>)?.value?.total
                    Choice(entry.label, pile == entry, { pile = entry }, count = total)
                }
            }
        }
        page?.notice?.takeIf { it.isNotEmpty() }?.let { item { Said(it) } }
        (load as? Load.Failed)?.let { item { Said(it.message, error = true) } }
        if (stale.isNotEmpty()) item { Said(stale, error = true) }
        if (page != null && items.isEmpty()) item { Said(if (pile == Pile.Looking) "Wishlist is empty." else "Nothing removed.") }
        items.forEach { want -> item(key = want.id) { WantRow(want) } }
    }
}

@Composable
private fun WantRow(want: AcquisitionTarget) {
    val api = LocalApi.current
    val act = rememberAction()
    val (icon, tone, word) = standing(want)
    val when_ = when {
        want.status == "not_wanted" -> ""
        want.waitingOnYou -> want.summary
        want.lastAttemptAt != null -> "tried ${relative(want.lastAttemptAt)}"
        want.nextAttemptAt != null -> "next try ${relative(want.nextAttemptAt)}"
        else -> "not searched yet"
    }
    Column {
        Row2(
            title = want.title,
            subtitle = listOf(want.artist, when_).filter { it.isNotEmpty() }.joinToString(" · "),
            leading = { CoverArt(want.originAlbumId?.let(api::coverUrl), want.originAlbumId ?: want.id) },
        ) {
            StateMark(icon, tone, word)
            if (want.status == "not_wanted") {
                QuietButton(null, Misprint.Undo, { act.run(Stale.Wants) { api.lookAgain(want.id) } }, enabled = !act.busy, description = "Add back", height = 40.dp)
            } else {
                QuietButton(null, Misprint.Ban, { act.run(Stale.Wants) { api.stopLooking(want.id) } }, enabled = !act.busy, description = "Remove", height = 40.dp)
            }
        }
        if (act.error.isNotEmpty()) Text(act.error, style = Type.small, color = Duoton.fail, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
        Rule()
    }
}
