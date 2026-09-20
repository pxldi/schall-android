@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.pxldi.schall.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/** A list screen: the list, pull to refresh, and what to say when it is
 * empty, loading or failed. A refresh that fails after a first read keeps the
 * list and says so above it. */
@Composable
fun <T> Listing(
    load: Load<List<T>>,
    refreshing: Boolean,
    stale: String,
    onRefresh: () -> Unit,
    empty: String,
    key: (T) -> Any,
    notice: String? = null,
    header: (LazyListScope.() -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when (load) {
            Load.Loading -> Centered { CircularProgressIndicator() }
            is Load.Failed -> Centered { Text(load.message, color = MaterialTheme.colorScheme.error) }
            is Load.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                header?.invoke(this)
                if (stale.isNotEmpty()) item { Note(stale, error = true) }
                if (notice != null) item { Note(notice) }
                if (load.value.isEmpty()) item { Note(empty) }
                items(load.value.size, key = { key(load.value[it]) }) { row(load.value[it]) }
            }
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

/** One sentence the screen has to say, in the flow of the list. */
@Composable
fun Note(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun ErrorLine(text: String) {
    if (text.isEmpty()) return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** A state word as a chip: what is happening to the thing on this row. */
@Composable
fun StateChip(word: String) {
    AssistChip(onClick = {}, enabled = false, label = { Text(word) })
}

/** The two piles a screen splits its list into, as one control at the top. */
@Composable
fun <K> Piles(value: K, options: List<Triple<K, String, Int?>>, onChange: (K) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        options.forEachIndexed { index, (key, label, count) ->
            SegmentedButton(
                selected = value == key,
                onClick = { onChange(key) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(if (count != null) "$label · $count" else label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** A row of a list: a title, a line under it, a state and some words, and
 * the controls that act on it. */
@Composable
fun ListRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
fun Meta(text: String, error: Boolean = false, maxLines: Int = 2) {
    if (text.isEmpty()) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A state chip and the words beside it, on one line. */
@Composable
fun StateLine(word: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StateChip(word)
        Meta(detail, maxLines = 2)
    }
}

/** Controls, right-aligned, wrapping when there are many. */
@Composable
fun Controls(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(0.dp))
        content()
    }
}

/** A release cover fetched with the phone's token, or a blank square where
 * the want traces back to no release. */
@Composable
fun Cover(url: String?, token: String, size: Int = 88) {
    val context = LocalContext.current
    val modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
    if (url == null) {
        Box(modifier.padding(0.dp)) {}
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $token").build())
            .build(),
        contentDescription = null,
        modifier = modifier,
    )
}
