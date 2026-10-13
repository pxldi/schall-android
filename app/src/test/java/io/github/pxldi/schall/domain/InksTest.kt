package io.github.pxldi.schall.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InksTest {
    // A picture as a Bitmap returns it: packed ARGB, one entry per pixel.
    private fun picture(vararg areas: Pair<IntArray, Int>): IntArray =
        areas.flatMap { (c, n) -> List(n) { (0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2] } }.toIntArray()

    private fun channels(hex: String) = hexToRgb(hex)

    @Test
    fun no_inks_given_means_the_house_inks() {
        assertEquals(clampInks(HOUSE_INKS), clampInks(null))
    }

    @Test
    fun a_pair_is_pushed_apart_until_text_reads_on_either() {
        val inks = clampInks("#6a4a8a", "#3a7a9a")
        assertTrue(luminance(inks.dark) <= 0.035)
        assertTrue(luminance(inks.light) >= 0.42)
        assertTrue(contrast(inks.dark, inks.light) >= 4.5)
    }

    @Test
    fun a_pair_that_arrives_reversed_is_swapped() {
        val inks = clampInks("#ffe0c0", "#101010")
        assertTrue(luminance(inks.dark) < luminance(inks.light))
    }

    @Test
    fun the_most_saturated_colour_is_the_light_ink_even_when_it_is_small() {
        val inks = inksFromPixels(picture(intArrayOf(10, 10, 12) to 900, intArrayOf(220, 170, 40) to 100))!!
        val (r, g, b) = channels(inks.light)
        assertTrue(r > b && g > b)
        assertTrue(luminance(inks.dark) <= 0.035)
    }

    @Test
    fun a_grey_cover_takes_its_lightest_colour() {
        val inks = inksFromPixels(picture(intArrayOf(30, 30, 30) to 500, intArrayOf(200, 200, 200) to 500))!!
        val c = channels(inks.light)
        assertTrue(c.max() - c.min() < 6)
        assertTrue(luminance(inks.light) >= 0.42)
    }

    @Test
    fun a_speck_under_two_percent_is_ignored() {
        val inks = inksFromPixels(picture(intArrayOf(20, 40, 120) to 990, intArrayOf(255, 0, 0) to 10))!!
        assertEquals(1, inks.palette?.size)
    }

    @Test
    fun a_transparent_picture_has_no_inks() {
        assertNull(inksFromPixels(intArrayOf(0, 0)))
    }

    @Test
    fun hex_round_trips_through_argb() {
        assertEquals(0xFF13204A.toInt(), argb("#13204a"))
        assertEquals("#13204a", rgbToHex(19.0, 32.0, 74.0))
    }
}
