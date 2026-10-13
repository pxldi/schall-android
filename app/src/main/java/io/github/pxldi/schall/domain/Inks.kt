package io.github.pxldi.schall.domain

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// The two inks a page is printed in (ADR Duoton), ported from the web's
// lib/duoton/inks.ts so a cover inks the phone the way it inks the browser.
// Pure functions on #rrggbb strings and packed ARGB pixels: no Android, so they
// run in unit tests.

/** A page's pair of inks, as #rrggbb. `palette` is the cover's main colours,
 * most of the picture first, when they are known. */
data class Inks(val dark: String, val light: String, val palette: List<String>? = null)

/** The inks a page uses when nothing on it has a cover. */
val HOUSE_INKS = Inks(dark = "#13204a", light = "#ffb59e")

fun hexToRgb(hex: String): IntArray {
    var h = hex.removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    val n = h.toIntOrNull(16) ?: 0
    return intArrayOf((n shr 16) and 255, (n shr 8) and 255, n and 255)
}

fun rgbToHex(r: Double, g: Double, b: Double): String =
    "#" + listOf(r, g, b).joinToString("") { v -> v.roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0') }

/** The packed 0xAARRGGBB form Android draws with. */
fun argb(hex: String): Int {
    val (r, g, b) = hexToRgb(hex)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** WCAG relative luminance, 0 for black and 1 for white. */
fun luminance(hex: String): Double {
    val (r, g, b) = hexToRgb(hex).map { v ->
        val s = v / 255.0
        if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

fun contrast(a: String, b: String): Double {
    val x = luminance(a)
    val y = luminance(b)
    return (max(x, y) + 0.05) / (min(x, y) + 0.05)
}

fun mix(a: String, b: String, t: Double): String {
    val x = hexToRgb(a)
    val y = hexToRgb(b)
    return rgbToHex(x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t)
}

/** HSL saturation and lightness, both 0 to 1. */
fun saturationLightness(r: Double, g: Double, b: Double): Pair<Double, Double> {
    val hi = max(r, max(g, b)) / 255
    val lo = min(r, min(g, b)) / 255
    val l = (hi + lo) / 2
    if (hi == lo) return 0.0 to l
    val d = hi - lo
    return (if (l > 0.5) d / (2 - hi - lo) else d / (hi + lo)) to l
}

private const val DARK_CEILING = 0.035
private const val LIGHT_FLOOR = 0.42

/** Clamps a pair so text stays legible on any cover: the dark ink is darkened
 * until its luminance is at most 0.035, the light ink lightened until it is at
 * least 0.42. A reversed pair is swapped first; a missing ink takes the house
 * one. */
fun clampInks(dark: String?, light: String?, palette: List<String>? = null): Inks {
    var d = dark?.takeIf { it.isNotEmpty() } ?: HOUSE_INKS.dark
    var l = light?.takeIf { it.isNotEmpty() } ?: HOUSE_INKS.light
    if (luminance(d) > luminance(l)) d = l.also { l = d }
    var n = 0
    while (luminance(d) > DARK_CEILING && n++ < 12) d = mix(d, "#000000", 0.18)
    n = 0
    while (luminance(l) < LIGHT_FLOOR && n++ < 12) l = mix(l, "#ffffff", 0.16)
    return Inks(d, l, palette)
}

fun clampInks(inks: Inks?): Inks = clampInks(inks?.dark, inks?.light, inks?.palette)

private class Cluster(val sum: DoubleArray, var count: Int) {
    fun mean() = doubleArrayOf(sum[0] / count, sum[1] / count, sum[2] / count)
}

/** Reads a pair of inks out of a cover's pixels (packed ARGB, as a Bitmap
 * returns them). The light ink is the most saturated main colour with
 * lightness between 0.22 and 0.9, else the lightest main colour; the dark ink
 * is the darkest main colour. Null when no pixel is opaque. */
fun inksFromPixels(pixels: IntArray): Inks? {
    // Colours are bucketed at 5 bits a channel, then the buckets are merged
    // greedily into at most eight clusters, largest first.
    val buckets = HashMap<Int, Cluster>()
    var total = 0
    for (p in pixels) {
        if ((p ushr 24) < 128) continue
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
        val bucket = buckets.getOrPut(key) { Cluster(DoubleArray(3), 0) }
        bucket.sum[0] += r.toDouble()
        bucket.sum[1] += g.toDouble()
        bucket.sum[2] += b.toDouble()
        bucket.count++
        total++
    }
    if (total == 0) return null

    val clusters = mutableListOf<Cluster>()
    for (bucket in buckets.values.sortedByDescending { it.count }) {
        val colour = bucket.mean()
        val near = clusters.firstOrNull {
            val m = it.mean()
            hypot(hypot(m[0] - colour[0], m[1] - colour[1]), m[2] - colour[2]) < 48
        }
        if (near != null) {
            for (i in 0..2) near.sum[i] += bucket.sum[i]
            near.count += bucket.count
        } else if (clusters.size < 8) {
            clusters += Cluster(bucket.sum.copyOf(), bucket.count)
        }
    }

    // A colour has to cover 2% of the picture to count, so a speck never inks a page.
    val main = clusters
        .filter { it.count.toDouble() / total >= 0.02 }
        .sortedByDescending { it.count }
        .map { it.mean() }
        .map { it to rgbToHex(it[0], it[1], it[2]) }
    if (main.isEmpty()) return null

    val vivid = main
        .map { (rgb, hex) -> hex to saturationLightness(rgb[0], rgb[1], rgb[2]) }
        .filter { (_, sl) -> sl.first >= 0.35 && sl.second >= 0.22 && sl.second <= 0.9 }
        .sortedByDescending { (_, sl) -> sl.first }
    val byLuminance = main.map { it.second }.sortedBy { luminance(it) }
    val light = vivid.firstOrNull()?.first ?: byLuminance.last()
    val dark = byLuminance.firstOrNull { it != light } ?: mix(light, "#000000", 0.8)
    return clampInks(dark, light, main.take(5).map { it.second })
}
