package io.github.pxldi.schall.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pxldi.schall.R
import io.github.pxldi.schall.domain.HOUSE_INKS
import io.github.pxldi.schall.domain.Inks
import io.github.pxldi.schall.domain.argb

/** The fixed ground every page is printed on (ADR Duoton). Only the print,
 * the primary button and the active tab take the page's inks; state keeps its
 * own colours whatever the cover. This replaces the Material You colours the
 * app started with: Poldi picked Duoton for the phone on 2026-10-13. */
object Duoton {
    val ground = Color(0xFF0D0F12)
    val panel = Color(0xFF14171C)
    val raise = Color(0xFF1B1F25)
    val line = Color(0xFF22262C)
    val line2 = Color(0xFF2D3239)
    val ink = Color(0xFFEEF0F2)
    val ink2 = Color(0xFFA5AAB2)
    val ink3 = Color(0xFF7C828B)

    val own = Color(0xFF9BE0B0)
    val want = Color(0xFFF2C66D)
    val fail = Color(0xFFF08A7A)
    val busy = Color(0xFF8EC5FF)
}

/** A page's two inks as colours. */
@Immutable
data class PageInks(val dark: Color, val light: Color) {
    companion object {
        fun of(inks: Inks) = PageInks(Color(argb(inks.dark)), Color(argb(inks.light)))
    }
}

val LocalInks = staticCompositionLocalOf { PageInks.of(HOUSE_INKS) }

/** Gives the content below a page's inks, easing from the ones before so a
 * page change fades rather than flashes. */
@Composable
fun ProvideInks(inks: Inks, content: @Composable () -> Unit) {
    val target = PageInks.of(inks)
    val dark by animateColorAsState(target.dark, tween(600), label = "dark ink")
    val light by animateColorAsState(target.light, tween(600), label = "light ink")
    CompositionLocalProvider(LocalInks provides PageInks(dark, light), content = content)
}

/** One face, Bricolage Grotesque. Body text uses the text cut and page titles
 * the display cut, as on the web. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_text_regular, FontWeight.Normal),
    Font(R.font.bricolage_text_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_text_bold, FontWeight.Bold),
)
val BricolageDisplay = FontFamily(Font(R.font.bricolage_display_extrabold, FontWeight.ExtraBold))

object Type {
    val title = TextStyle(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, lineHeight = 64.sp, letterSpacing = (-0.055).em)
    val figure = TextStyle(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.04).em)
    val name = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
    val body = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val small = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em)
    val button = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
}

private val scheme = darkColorScheme(
    primary = Duoton.ink,
    onPrimary = Duoton.ground,
    primaryContainer = Duoton.raise,
    onPrimaryContainer = Duoton.ink,
    secondary = Duoton.ink2,
    onSecondary = Duoton.ground,
    secondaryContainer = Duoton.raise,
    onSecondaryContainer = Duoton.ink,
    tertiary = Duoton.busy,
    background = Duoton.ground,
    onBackground = Duoton.ink,
    surface = Duoton.ground,
    onSurface = Duoton.ink,
    surfaceVariant = Duoton.panel,
    onSurfaceVariant = Duoton.ink2,
    surfaceContainerLowest = Duoton.ground,
    surfaceContainerLow = Duoton.panel,
    surfaceContainer = Duoton.panel,
    surfaceContainerHigh = Duoton.raise,
    surfaceContainerHighest = Duoton.raise,
    outline = Duoton.line2,
    outlineVariant = Duoton.line,
    error = Duoton.fail,
    onError = Duoton.ground,
)

private fun Typography.inBricolage(): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold),
    displayMedium = displayMedium.copy(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold),
    displaySmall = displaySmall.copy(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold),
    headlineLarge = headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    headlineMedium = headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    headlineSmall = headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    titleLarge = titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    titleMedium = titleMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
    titleSmall = titleSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
    bodyLarge = bodyLarge.copy(fontFamily = Bricolage),
    bodyMedium = bodyMedium.copy(fontFamily = Bricolage),
    bodySmall = bodySmall.copy(fontFamily = Bricolage),
    labelLarge = labelLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    labelMedium = labelMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
    labelSmall = labelSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
)

/** Always dark: Duoton has no light mode. Material components below keep
 * working and take the ground, the inks of text and the state colours. */
@Composable
fun SchallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography().inBricolage()) {
        // Text and icons outside a Material surface would otherwise be black.
        CompositionLocalProvider(LocalContentColor provides Duoton.ink) {
            ProvideInks(HOUSE_INKS, content)
        }
    }
}
