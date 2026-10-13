package io.github.pxldi.schall.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.LocalInks
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import kotlinx.coroutines.delay

/** The screen's one preview player. Each copy card has its own play button;
 * pressing one switches the player to that copy. The audio route answers
 * Range, so seeking works, and the whole track is served: a copy reaches the
 * queue because nothing could settle it, and somebody unsure needs to move
 * around inside it. */
@Stable
class Preview(private val player: ExoPlayer, private val address: (String) -> String) {
    var current by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        internal set
    var position by mutableLongStateOf(0L)
        internal set
    var duration by mutableLongStateOf(0L)
        internal set

    /** Plays the copy, or pauses it when it is the one playing. */
    fun toggle(copyId: String) {
        if (current == copyId) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        current = copyId
        position = 0
        duration = 0
        player.setMediaItem(MediaItem.fromUri(address(copyId)))
        player.prepare()
        player.play()
    }

    fun seek(fraction: Float) {
        if (duration > 0) player.seekTo((fraction * duration).toLong())
    }

    internal fun read() {
        playing = player.isPlaying
        position = player.currentPosition.coerceAtLeast(0)
        duration = if (player.duration == C.TIME_UNSET) 0 else player.duration
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberPreview(): Preview {
    val api = LocalApi.current
    val context = LocalContext.current
    val player = remember(api) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${api.session.token}"))
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val preview = remember(player) { Preview(player, api::copyAudioUrl) }
    LaunchedEffect(preview) {
        while (true) {
            preview.read()
            delay(250)
        }
    }
    return preview
}

/** The round play button of a card: the light ink while it plays. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val inks = LocalInks.current
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).clip(CircleShape).background(if (playing) inks.light else Duoton.raise),
    ) {
        MisprintIcon(if (playing) Misprint.Pause else Misprint.Play, tint = if (playing) inks.dark else Duoton.ink, description = if (playing) "Pause" else "Play")
    }
}

/** Where the copy is, and the handle to move inside it. */
@Composable
fun Scrubber(preview: Preview, modifier: Modifier = Modifier) {
    val inks = LocalInks.current
    val fraction = if (preview.duration > 0) (preview.position.toFloat() / preview.duration).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = fraction,
            onValueChange = preview::seek,
            enabled = preview.duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = inks.light,
                activeTrackColor = inks.light,
                inactiveTrackColor = Duoton.line2,
                disabledThumbColor = Duoton.line2,
                disabledInactiveTrackColor = Duoton.line2,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Seek" },
        )
    }
}
