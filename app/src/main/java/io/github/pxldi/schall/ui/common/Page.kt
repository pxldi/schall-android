package io.github.pxldi.schall.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.pxldi.schall.domain.Inks
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.LocalInks
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import io.github.pxldi.schall.ui.theme.Type

/** A page in Duoton: the print at the top with the page's title over it, at
 * the same place on every page, and the page's list below. The print scrolls
 * away with the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintedPage(
    title: String,
    covers: List<CoverRef>,
    mode: PrintMode = PrintMode.Strip,
    printHeight: Dp = 260.dp,
    subtitle: String = "",
    titleSize: Int = 72,
    back: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    belowTitle: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val inks = coverInks(covers)
    UsePageInks(inks)
    val page = LocalPage.current
    val list = @Composable {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "page-header", contentType = "page-header") {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(printHeight)) {
                        Print(covers, mode, inks ?: page.inks, Modifier.matchParentSize())
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp).height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (back != null) IconButton(onClick = back) { MisprintIcon(Misprint.ChevronLeft, description = "Back") }
                            Box(Modifier.weight(1f))
                            actions()
                        }
                        Text(
                            title,
                            style = Type.title.copy(fontSize = titleSize.sp, lineHeight = (titleSize * 0.88f).sp),
                            color = Duoton.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp).semantics { heading() },
                        )
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = Type.body, color = Duoton.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
                    }
                    Box(Modifier.padding(top = 12.dp)) { belowTitle() }
                }
            }
            content()
        }
    }
    if (onRefresh != null) PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { list() }
    else list()
}

/** A cover at a size, or the generated stand-in when there is none or it
 * fails, so no row falls back to a grey square. */
@Composable
fun CoverArt(url: String?, seed: String, inks: Inks? = null, size: Dp = 44.dp, shape: Shape = RoundedCornerShape(6.dp)) {
    val token = LocalApi.current.session.token
    val context = LocalContext.current
    // An unspecified size fills whatever box the caller gave, as a grid tile does.
    val modifier = (if (size.isSpecified) Modifier.size(size) else Modifier.fillMaxSize()).clip(shape)
    val fallback = @Composable { GeneratedCover(seed, inks, modifier) }
    if (url == null) {
        fallback()
        return
    }
    val px = if (size.isSpecified) size.value.toInt() * 2 else 512
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(Pictures.sized(url, if (px <= 128) 128 else 256))
            .httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $token").build())
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().background(Duoton.raise)) },
        error = { fallback() },
    )
}

@Composable
fun GeneratedCover(seed: String, inks: Inks?, modifier: Modifier) {
    val bitmap = remember(seed, inks) {
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { drawGenerated(Canvas(it), 128f, 128f, seed, inks) }.asImageBitmap()
    }
    Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** The page's main action: the light ink with the dark ink on it. */
@Composable
fun PrimaryButton(label: String, icon: Misprint?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 48.dp) {
    val inks = LocalInks.current
    Row(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (enabled) inks.light else Duoton.raise)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colour = if (enabled) inks.dark else Duoton.ink3
        if (icon != null) MisprintIcon(icon, tint = colour, size = 18.dp)
        Text(label, style = Type.button, color = colour, maxLines = 1)
    }
}

/** A secondary control on the raised panel. With no label it is an icon
 * button, named by `description`. */
@Composable
fun QuietButton(label: String?, icon: Misprint?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, description: String? = null, tint: Color = Duoton.ink, height: Dp = 48.dp) {
    Row(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(height / 2))
            .background(Duoton.raise)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { if (label == null && description != null) this[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] = listOf(description) }
            .padding(horizontal = if (label == null) 14.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colour = if (enabled) tint else Duoton.ink3
        if (icon != null) MisprintIcon(icon, tint = colour, size = 18.dp)
        if (label != null) Text(label, style = Type.button.copy(fontSize = 14.sp), color = colour, maxLines = 1)
    }
}

/** One choice of a few, as a pill: the list a page shows. */
@Composable
fun Choice(label: String?, selected: Boolean, onClick: () -> Unit, icon: Misprint? = null, count: Int? = null, tint: Color = Duoton.ink2, description: String? = null) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Duoton.ink) else Modifier.border(1.dp, Duoton.line2, shape))
            .clickable(role = Role.Tab, onClickLabel = description ?: label, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colour = if (selected) Duoton.ground else tint
        CompositionLocalProvider(LocalContentColor provides colour) {
            if (icon != null) MisprintIcon(icon, size = 16.dp, description = if (label == null) description else null)
            if (label != null) Text(label, style = Type.name.copy(fontSize = 13.sp), color = colour)
            if (count != null) Text(count.toString(), style = Type.name.copy(fontSize = 13.sp), color = colour)
        }
    }
}

@Composable
fun Choices(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** What state a thing is in, in the state's own colour: never the page's inks. */
enum class Tone(val colour: Color) {
    Own(Duoton.own),
    Want(Duoton.want),
    Fail(Duoton.fail),
    Busy(Duoton.busy),
    Quiet(Duoton.ink2),
}

@Composable
fun Chip(label: String, tone: Tone = Tone.Quiet, icon: Misprint? = null) {
    Row(
        Modifier
            .heightIn(min = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (tone == Tone.Quiet) Duoton.raise else tone.colour.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) MisprintIcon(icon, size = 13.dp, tint = tone.colour)
        Text(label, style = Type.small.copy(fontSize = 12.sp), color = tone.colour, maxLines = 1)
    }
}

/** A small round state mark for a row, named for assistive technology. */
@Composable
fun StateMark(icon: Misprint, tone: Tone, description: String) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(tone.colour.copy(alpha = 0.12f)).semantics {
            this[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] = listOf(description)
        },
        contentAlignment = Alignment.Center,
    ) { MisprintIcon(icon, size = 15.dp, tint = tone.colour) }
}

/** A section's small heading inside a page. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.label, color = Duoton.ink3, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp).semantics { heading() })
}

/** One row of a list: picture, two lines, and whatever sits at the end. */
@Composable
fun Row2(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColour: Color = Duoton.ink,
    subtitleColour: Color = Duoton.ink2,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.name, color = titleColour, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = Type.body, color = subtitleColour, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** A thin line between rows. */
@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).background(Duoton.raise))
}

/** Something the page has to say in place of its list. */
@Composable
fun Said(text: String, error: Boolean = false) {
    Text(text, style = Type.body, color = if (error) Duoton.fail else Duoton.ink2, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp))
}


/** The want control on a song or release row: an outlined plus, or the light
 * ink with a dark tick once wanted. Pressing the tick removes the want. */
@Composable
fun WantToggle(wanted: Boolean, name: String, onClick: () -> Unit, enabled: Boolean = true) {
    val inks = LocalInks.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (wanted) "Remove $name" else "Want $name", onClick = onClick)
            .semantics { this[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] = listOf(if (wanted) "Remove $name" else "Want $name") },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(if (wanted) Modifier.background(inks.light) else Modifier.border(2.dp, Duoton.line2, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            MisprintIcon(if (wanted) Misprint.Check else Misprint.Plus, size = 16.dp, tint = if (wanted) inks.dark else Duoton.ink)
        }
    }
}
