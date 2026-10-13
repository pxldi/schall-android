package io.github.pxldi.schall.domain

// The arithmetic of the print (ADR Duoton), ported from the web's
// lib/duoton/paint.ts. The drawing itself is in ui/common/Print.kt; this part
// works on plain pixel arrays so it runs in unit tests.

private fun hash(seed: String): Int {
    var h = 2166136261L.toInt()
    for (c in seed) h = (h xor c.code) * 16777619
    return h
}

/** A small seeded generator, so a release draws the same generated cover and
 * the same grain on every visit, and the same as in the browser. */
fun seeded(seed: String): () -> Double {
    var h = hash(seed)
    return {
        h = (h xor (h ushr 15)) * -2048144789 // 2246822507 as a signed int
        h = (h xor (h ushr 13)) * -1028477387 // 3266489909 as a signed int
        h = h xor (h ushr 16)
        (h.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

/** Maps every pixel between the two inks by its lightness, with light grain.
 * The lightness range is read from a sample and stretched to fill both inks,
 * so a dim cover still prints from one ink to the other. Works in place on
 * packed ARGB pixels and leaves them opaque. */
fun duotone(pixels: IntArray, inks: Inks) {
    val dark = hexToRgb(inks.dark)
    val light = hexToRgb(inks.light)
    fun lightness(p: Int) = (((p shr 16) and 255) * 0.3 + ((p shr 8) and 255) * 0.59 + (p and 255) * 0.11) / 255
    var lo = 1.0
    var hi = 0.0
    var i = 0
    while (i < pixels.size) {
        val v = lightness(pixels[i])
        if (v < lo) lo = v
        if (v > hi) hi = v
        i += 16
    }
    val span = maxOf(0.15, hi - lo)
    val grain = seeded("grain")
    for (n in pixels.indices) {
        val v = ((lightness(pixels[n]) - lo) / span + (grain() - 0.5) * 0.06).coerceIn(0.0, 1.0)
        val r = (dark[0] + (light[0] - dark[0]) * v).toInt()
        val g = (dark[1] + (light[1] - dark[1]) * v).toInt()
        val b = (dark[2] + (light[2] - dark[2]) * v).toInt()
        pixels[n] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
