package io.github.pxldi.schall.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.pxldi.schall.domain.HOUSE_INKS
import io.github.pxldi.schall.domain.Inks
import io.github.pxldi.schall.domain.argb
import io.github.pxldi.schall.domain.clampInks
import io.github.pxldi.schall.domain.duotone
import io.github.pxldi.schall.domain.inksFromPixels
import io.github.pxldi.schall.domain.mix
import io.github.pxldi.schall.domain.seeded
import io.github.pxldi.schall.ui.theme.Duoton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/** A picture in a print: a cover Schall serves, or none, plus the seed a
 * generated stand-in is drawn from and the stored inks when the API sent them. */
data class CoverRef(val url: String?, val seed: String, val inks: Inks? = null)

enum class PrintMode {
    /** Covers side by side: Overview, Review, Downloads. */
    Strip,

    /** The first cover large with a second screened over it: a release, an artist, a song. */
    Single,
}

/** The open page's inks. The bottom bar and the primary buttons read them, so
 * a page sets them through [UsePageInks] rather than providing its own. */
@Stable
class PageState {
    var inks by mutableStateOf(HOUSE_INKS)
}

val LocalPage = staticCompositionLocalOf { PageState() }

/** Sets the open page's inks once they are known. The ones already up stay
 * until then, so a page change fades from one pair to the next instead of
 * flashing through the house inks. */
@Composable
fun UsePageInks(inks: Inks?) {
    val page = LocalPage.current
    LaunchedEffect(inks) { if (inks != null) page.inks = inks }
}

/** The inks a list of covers prints in: the first stored pair, else the pair
 * read from the first cover's pixels, else the house inks. Null while the
 * cover is still being read. */
@Composable
fun coverInks(covers: List<CoverRef>): Inks? {
    val context = LocalContext.current
    val token = LocalApi.current.session.token
    val stored = covers.firstNotNullOfOrNull { it.inks }
    val url = covers.firstNotNullOfOrNull { it.url }
    val read by produceState<Inks?>(initialValue = null, stored, url) {
        value = when {
            stored != null -> clampInks(stored)
            url == null -> if (covers.isEmpty()) null else HOUSE_INKS
            else -> Pictures.inksOf(context, url, token) ?: HOUSE_INKS
        }
    }
    return read
}

/** Covers loaded once a process, small, in software so their pixels can be
 * read. Schall serves 128 and 256 pixel copies (`?size=`), plenty for a print
 * that is blurred on purpose. */
object Pictures {
    private val inks = ConcurrentHashMap<String, Inks>()

    fun sized(url: String, width: Int): String =
        if (url.contains("/api/")) url + (if (url.contains('?')) "&" else "?") + "size=$width" else url

    suspend fun load(context: Context, url: String, token: String, width: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(sized(url, width))
            .httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $token").build())
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    suspend fun inksOf(context: Context, url: String, token: String): Inks? {
        inks[url]?.let { return it }
        val picture = load(context, url, token, 128) ?: return null
        val read = withContext(Dispatchers.Default) {
            val small = Bitmap.createScaledBitmap(picture, 48, 48, true)
            val pixels = IntArray(48 * 48)
            small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
            inksFromPixels(pixels)
        } ?: return null
        inks[url] = read
        return read
    }
}

/** The duotone print (ADR Duoton): covers softened and mapped between the two
 * inks, fading into the ground at the bottom. It is drawing only and says
 * nothing to assistive technology. The bitmap is made at one pixel per dp:
 * the print is soft on purpose, and a full-density copy would cost three to
 * four times the work for nothing anyone could see. */
@Composable
fun Print(covers: List<CoverRef>, mode: PrintMode, inks: Inks, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val token = LocalApi.current.session.token
    BoxWithConstraints(modifier.background(Color(argb(inks.dark)))) {
        val w = maxWidth.value.toInt().coerceAtLeast(1)
        val h = maxHeight.value.toInt().coerceAtLeast(1)
        val printed by produceState<Bitmap?>(initialValue = null, covers, mode, inks, w, h) {
            value = coroutineScope {
                val pictures = covers.map { cover ->
                    async { cover.url?.let { Pictures.load(context, it, token, if (mode == PrintMode.Single) 256 else 128) } }
                }.awaitAll()
                withContext(Dispatchers.Default) { paintPrint(w, h, covers, pictures, mode, inks) }
            }
        }
        printed?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Duoton.ground)))
    }
}

/** Softens covers a little and lifts their contrast, like the web's
 * `blur() contrast(1.15)`. The blur comes from drawing at half size and
 * scaling back up. */
private val soften = Paint(Paint.FILTER_BITMAP_FLAG).apply {
    colorFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, -19f,
                0f, 1.15f, 0f, 0f, -19f,
                0f, 0f, 1.15f, 0f, -19f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

private fun square(cover: CoverRef, picture: Bitmap?, size: Int): Bitmap {
    val out = Bitmap.createBitmap(max(1, size), max(1, size), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    if (picture != null) canvas.drawBitmap(picture, null, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
    else drawGenerated(canvas, size.toFloat(), size.toFloat(), cover.seed, cover.inks)
    return out
}

/** Lays the covers out as the web's paintPrint does, then maps them between
 * the inks. */
fun paintPrint(w: Int, h: Int, covers: List<CoverRef>, pictures: List<Bitmap?>, mode: PrintMode, inks: Inks): Bitmap? {
    if (covers.isEmpty()) return null
    val sw = max(1, ceil(w / 2.0).toInt())
    val sh = max(1, ceil(h / 2.0).toInt())
    val soft = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
    val sg = Canvas(soft)
    if (mode == PrintMode.Strip) {
        // Each cover is a square big enough to cover its tile, cropped to the
        // tile's middle, so no cover is ever stretched.
        val s = ceil(sw.toDouble() / covers.size).toInt()
        val side = max(s, sh)
        covers.forEachIndexed { i, cover ->
            val tile = square(cover, pictures[i], side)
            val from = Rect((side - s) / 2, (side - sh) / 2, (side - s) / 2 + s, (side - sh) / 2 + sh)
            sg.drawBitmap(tile, from, Rect(i * s, 0, i * s + s, sh), soften)
        }
    } else {
        val s = sw
        val first = square(covers[0], pictures[0], s)
        val top = (s * 0.2).toInt()
        sg.drawBitmap(first, Rect(0, top, s, top + s * sh / sw), Rect(0, 0, sw, sh), soften)
        if (covers.size > 1) {
            val second = square(covers[1], pictures[1], s)
            val screen = Paint(soften).apply {
                alpha = 102
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            val from = (s * 0.4).toInt()
            sg.drawBitmap(second, Rect(0, from, s, from + s * sh / sw), Rect((sw * 0.1).toInt(), 0, sw + (sw * 0.1).toInt(), sh), screen)
        }
    }
    val out = Bitmap.createScaledBitmap(soft, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(w * h)
    out.getPixels(pixels, 0, w, 0, 0, w, h)
    duotone(pixels, inks)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

/** A stand-in cover for something with no art, so no page falls back to a
 * grey square. The seed picks the pattern, so a release always gets the same
 * picture, and it is the picture the web draws for it. */
fun drawGenerated(g: Canvas, w: Float, h: Float, seed: String, stored: Inks?) {
    val rnd = seeded(seed)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val palette = stored?.palette
    if (palette != null && palette.size > 1) {
        g.drawColor(argb(palette[0]))
        val kind = (rnd() * 3).toInt()
        palette.drop(1).forEachIndexed { i, c ->
            fill.color = argb(c)
            val k = 1f - i.toFloat() / palette.size
            when (kind) {
                0 -> g.drawRect(0f, h * (1 - k * 0.55f), w, h * (1 - k * 0.55f) + h * k * 0.1f + 2, fill)
                1 -> g.drawCircle(w * (0.3f + rnd().toFloat() * 0.4f), h * (0.35f + rnd().toFloat() * 0.3f), w * k * 0.32f, fill)
                else -> {
                    val x = w * rnd().toFloat() * 0.6f
                    val y = h * rnd().toFloat() * 0.6f
                    g.drawRect(x, y, x + w * k * 0.45f, y + h * k * 0.45f, fill)
                }
            }
        }
        return
    }

    val inks = clampInks(stored)
    g.drawColor(argb(mix(inks.dark, inks.light, 0.18)))
    val ink = argb(inks.light)
    fill.color = ink
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ink
    }
    fun alpha(a: Double) = (a.coerceIn(0.0, 1.0) * 255).toInt()
    when ((rnd() * 4).toInt()) {
        0 -> repeat(7) {
            fill.alpha = alpha(0.25 + rnd() * 0.6)
            val x = w * rnd().toFloat()
            val y = h * rnd().toFloat()
            g.drawCircle(x, y, w * (0.05f + rnd().toFloat() * 0.18f), fill)
        }
        1 -> {
            line.strokeWidth = w * 0.02f
            for (i in 0 until 9) {
                line.alpha = alpha(0.3 + i * 0.07)
                val path = Path()
                var x = 0f
                while (x <= w) {
                    val y = h * (0.15f + i * 0.09f) + sin((x / w) * 6 + i) * h * 0.03f
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 3f
                }
                g.drawPath(path, line)
            }
        }
        2 -> {
            val n = 4 + (rnd() * 3).toInt()
            for (i in 0 until n) for (j in 0 until n) {
                fill.alpha = alpha(rnd() * 0.85)
                val x = w * (0.08f + i * 0.84f / n)
                val y = h * (0.08f + j * 0.84f / n)
                g.drawRect(x, y, x + w * 0.84f / n - 3, y + h * 0.84f / n - 3, fill)
            }
        }
        else -> {
            line.strokeWidth = w * 0.012f
            for (i in 1 until 12) {
                line.alpha = alpha(1 - i * 0.075)
                val r = w * i * 0.07f
                g.drawArc(RectF(w * 0.5f - r, h * 1.05f - r, w * 0.5f + r, h * 1.05f + r), 180f, 180f, false, line)
            }
        }
    }
}
