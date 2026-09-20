package io.github.pxldi.schall.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.pxldi.schall.data.AcquiredCopy
import io.github.pxldi.schall.domain.formatDuration
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.Meta
import kotlinx.coroutines.delay

private val volumes = listOf(0.25f, 0.5f, 0.75f, 1f)

/** The preview player. One player for the screen; choosing a card replaces
 * its source. The audio route answers Range, so seeking works. The whole
 * track is served: a copy reaches the queue because no identifier could
 * settle it, and somebody unsure needs to move around inside it. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun Player(copy: AcquiredCopy?) {
    val api = LocalApi.current
    val context = LocalContext.current
    val player = remember(api) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${api.session.token}"))
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var volume by rememberSaveable { mutableFloatStateOf(1f) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player, copy?.id) {
        if (copy == null) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(api.copyAudioUrl(copy.id)))
        player.prepare()
        player.play()
    }
    LaunchedEffect(player, volume) { player.volume = volume }
    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0)
            duration = if (player.duration == androidx.media3.common.C.TIME_UNSET) 0 else player.duration
            delay(250)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    enabled = copy != null,
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        copy?.name ?: "Choose a copy to hear it.",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Meta("${formatDuration(position).ifEmpty { "0:00" }} / ${formatDuration(duration).ifEmpty { "0:00" }}")
                }
            }
            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (duration > 0) player.seekTo((it * duration).toLong()) },
                enabled = duration > 0,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meta("Volume")
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    volumes.forEachIndexed { index, step ->
                        SegmentedButton(
                            selected = volume == step,
                            onClick = { volume = step },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = volumes.size),
                            label = { Text("${(step * 100).toInt()}") },
                        )
                    }
                }
            }
        }
    }
}
