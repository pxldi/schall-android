package io.github.pxldi.schall.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuotoneTest {
    @Test
    fun the_seeded_generator_draws_what_the_browser_draws() {
        // Values from the web's seeded() in lib/duoton/paint.ts, so a generated
        // cover and the grain look the same on both.
        val grain = seeded("grain")
        assertEquals(0.3831385695375502, grain(), 1e-12)
        assertEquals(0.21193072060123086, grain(), 1e-12)
        assertEquals(0.04454512777738273, grain(), 1e-12)
        assertEquals(0.27716770535334945, seeded("r1")(), 1e-12)
    }

    @Test
    fun the_darkest_pixel_prints_near_the_dark_ink_and_the_lightest_near_the_light_ink() {
        val inks = Inks("#000000", "#ffffff")
        val pixels = intArrayOf(0xFF101010.toInt(), 0xFF808080.toInt(), 0xFFF0F0F0.toInt())
        duotone(pixels, inks)
        assertTrue((pixels[0] and 255) < 20)
        assertTrue((pixels[2] and 255) > 235)
        assertTrue(pixels.all { (it ushr 24) == 255 })
    }
}
