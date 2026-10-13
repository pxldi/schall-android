package io.github.pxldi.schall.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxldi.schall.data.Overview
import io.github.pxldi.schall.domain.formatBytes
import io.github.pxldi.schall.ui.common.CoverArt
import io.github.pxldi.schall.ui.common.CoverRef
import io.github.pxldi.schall.ui.common.Load
import io.github.pxldi.schall.ui.common.Loader
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.Said
import io.github.pxldi.schall.ui.common.SectionLabel
import io.github.pxldi.schall.ui.review.reviewCount
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import io.github.pxldi.schall.ui.theme.Type
import java.text.NumberFormat
import java.time.ZoneId

/** The phone's first page: how much waits for a decision, what is
 * downloading, and what arrived lately. The listening figures stay on the web,
 * where there is room for them. */
@Composable
fun OverviewScreen(openReview: () -> Unit, openDownloads: () -> Unit, openWants: () -> Unit, openSettings: () -> Unit, openRelease: (String) -> Unit) {
    val api = LocalApi.current
    val store = LocalAppStore.current
    // Read once and on a pull: nothing on this page changes while it is looked at.
    val loader = viewModel(store, key = "overview") { Loader { api.overview(ZoneId.systemDefault().id) } }
    val load by loader.state.collectAsStateWithLifecycle()
    val refreshing by loader.refreshing.collectAsStateWithLifecycle()
    val stale by loader.stale.collectAsStateWithLifecycle()
    val overview = (load as? Load.Ready<Overview>)?.value
    val waiting = reviewCount()

    val covers = overview?.recentlyAdded.orEmpty()
        .distinctBy { it.releaseId ?: it.coverUrl ?: it.title }
        .take(3)
        .map { CoverRef(api.picture(it.coverUrl), it.releaseId ?: it.title, it.inks?.toInks()) }

    PrintedPage(
        title = "Overview",
        covers = covers,
        printHeight = 300.dp,
        subtitle = overview?.let { "${NumberFormat.getIntegerInstance().format(it.library.fileCount)} files · ${formatBytes(it.library.totalBytes)}" }.orEmpty(),
        refreshing = refreshing,
        onRefresh = loader::refresh,
        actions = { IconButton(onClick = openSettings) { MisprintIcon(Misprint.Settings, description = "Settings") } },
    ) {
        item(key = "figures") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure(waiting.toString(), "Review", Misprint.Checklist, Duoton.want, openReview, Modifier.weight(1f))
                Figure((overview?.arrived?.downloading ?: 0).toString(), "Downloading", Misprint.Download, Duoton.busy, openDownloads, Modifier.weight(1f))
            }
        }
        item(key = "wishlist") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).clip(RoundedCornerShape(16.dp)).background(Duoton.panel)
                    .clickable(role = Role.Button, onClick = openWants).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MisprintIcon(Misprint.Want, tint = Duoton.ink2)
                Text("Wishlist", style = Type.name, color = Duoton.ink, modifier = Modifier.weight(1f))
                MisprintIcon(Misprint.ChevronRight, tint = Duoton.ink3, size = 18.dp)
            }
        }
        when (val state = load) {
            is Load.Failed -> item { Said(state.message, error = true) }
            else -> {}
        }
        if (stale.isNotEmpty()) item { Said(stale, error = true) }
        val added = overview?.recentlyAdded.orEmpty()
        if (added.isNotEmpty()) {
            item(key = "added-label") { SectionLabel("Recently added") }
            item(key = "added") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(added, key = { "${it.trackId}:${it.addedAt}:${it.title}" }) { row ->
                        Column(
                            Modifier.width(124.dp).clip(RoundedCornerShape(10.dp))
                                .then(if (row.releaseId != null) Modifier.clickable { openRelease(row.releaseId) } else Modifier),
                        ) {
                            CoverArt(api.picture(row.coverUrl), row.releaseId ?: row.title, row.inks?.toInks(), size = 124.dp, shape = RoundedCornerShape(10.dp))
                            Text(row.title, style = Type.name.copy(fontSize = Type.body.fontSize), color = Duoton.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                            Text(row.artist, style = Type.body, color = Duoton.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String, icon: Misprint, tone: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Duoton.panel).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MisprintIcon(icon, tint = tone)
            Text(value, style = Type.figure, color = tone)
        }
        Text(label, style = Type.body, color = Duoton.ink2)
    }
}
