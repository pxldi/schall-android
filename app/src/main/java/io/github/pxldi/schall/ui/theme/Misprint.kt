package io.github.pxldi.schall.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Misprint icon set (ADR Duoton), the same paths as the web's
 * lib/icons.ts: a sharp 2px line in the text colour on a 24 grid. Nothing else
 * in the app draws an icon. A new icon is added to both lists. */
enum class Misprint(vararg val paths: String) {
    Alert("M12 3 22 20H2Z", "M12 9v5M12 16.5v1.5"),
    ArrowDown("M12 4v16M6 14l6 6 6-6"),
    ArrowLeft("M20 12H4M10 6l-6 6 6 6"),
    ArrowRight("M4 12h16M14 6l6 6-6 6"),
    ArrowUp("M12 20V4M6 10l6-6 6 6"),
    ArrowUpRight("M6 18 18 6M8 6h10v10"),
    Artist("M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8Z", "M4 21a8 7 0 0 1 16 0"),
    Artists("M9 4.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7Z", "M2 20a7 6 0 0 1 14 0", "M15 4.6a3.5 3.5 0 0 1 0 6.8M18 13.6a6 6 0 0 1 4 6.4"),
    Ban("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z", "M5.6 5.6l12.8 12.8"),
    Busy("M12 3a9 9 0 1 1-9 9"),
    Check("M4 12.5l5 5L20 6.5"),
    Checklist("M3 6l2 2 3-3M3 13l2 2 3-3", "M12 7h9M12 14h9M12 20h9"),
    ChevronDown("M6 9l6 6 6-6"),
    ChevronLeft("M15 6l-6 6 6 6"),
    ChevronRight("M9 6l6 6-6 6"),
    ChevronUp("M6 15l6-6 6 6"),
    Circle("M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16Z"),
    Close("M5 5l14 14M19 5 5 19"),
    Delete("M6 7l1 13h10l1-13Z", "M4 7h16M9 7V4h6v3"),
    Device("M5 3h14v12H5Z", "M9 21h6M12 15v6"),
    Download("M12 3v12M6.5 9.5 12 15l5.5-5.5M4 20h16"),
    Edit("M4 20l1-5L16 4l4 4L9 19Z", "M14 6l4 4"),
    Enter("M20 4v7a3 3 0 0 1-3 3H5", "M9 10l-4 4 4 4"),
    External("M4 6h6M4 6v14h14v-6", "M14 4h6v6M20 4l-9 9"),
    FileUnknown("M5 3h9l5 5v13H5Z", "M10 11a2 2 0 1 1 3 1.7c-.7.4-1 .9-1 1.6M12 17v1.5"),
    Filter("M3 4h18l-7 8.5V20l-4-2v-5.5Z"),
    Fingerprint("M6 18.5a10 10 0 0 1-1-4.5a7 7 0 0 1 14 0v1M9 20a12 12 0 0 1-1-6a4 4 0 0 1 8 0v2a6 6 0 0 1-1 3M12 14v2a9 9 0 0 1-1.5 5"),
    First("M6 5v14", "M17 6l-6 6 6 6"),
    FolderAdd("M3 5h6l2 2h10v12H3Z", "M12 10v6M9 13h6"),
    Follow("M10 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8Z", "M3 21a7 7 0 0 1 14 0", "M15.5 9l2 2 4-4"),
    Heart("M12 20 4.2 12.2A4.6 4.6 0 0 1 12 6.3a4.6 4.6 0 0 1 7.8 5.9Z"),
    Hide("M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z", "M4 4l16 16"),
    Image("M3 4h18v16H3Z", "M3 17l5-5 4 4 3-3 6 6", "M15 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3Z"),
    Info("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z", "M12 11v6M12 7v1.5"),
    Last("M18 5v14", "M7 6l6 6-6 6"),
    Library("M4 4h4v16H4Z", "M10 4h4v16h-4Z", "M15 5l3.5-1 3.5 15-3.5 1Z"),
    Link("M10 14l4-4", "M8.5 11.5 6 14a3.5 3.5 0 0 0 5 5l2.5-2.5M15.5 12.5 18 10a3.5 3.5 0 0 0-5-5l-2.5 2.5"),
    Listen("M4 16v-4a8 8 0 0 1 16 0v4", "M3 14h4v7H3Z", "M17 14h4v7h-4Z"),
    Minus("M5 12h14"),
    More("M5 11h2v2H5ZM11 11h2v2h-2ZM17 11h2v2h-2Z"),
    Pause("M6 4h4v16H6Z", "M14 4h4v16h-4Z"),
    Play("M7 4v16l13-8Z"),
    Playlist("M4 6h11M4 11h11M4 16h6", "M16 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5Z", "M18.5 18V8l2.5-.8"),
    Plus("M12 4v16M4 12h16"),
    Refresh("M20 5v6h-6", "M19.4 11A7.5 7.5 0 1 0 17.6 17"),
    Release("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z", "M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5Z", "M12 6.5A5.5 5.5 0 0 1 17.5 12"),
    Save("M4 3h13l3 3v15H4Z", "M8 3v5h7V3M7 21v-7h10v7"),
    Search("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13Z", "M15.5 15.5 21 21"),
    SearchCheck("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13Z", "M15.5 15.5 21 21", "M7.5 10.5l2 2 3.5-3.5"),
    Send("M3 11 21 3l-8 18-2.5-7.5Z", "M10.5 13.5 21 3"),
    Settings("M21.32 10.15L21.32 13.85L18.87 13.37L17.82 15.89L19.90 17.28L17.28 19.90L15.89 17.82L13.37 18.87L13.85 21.32L10.15 21.32L10.63 18.87L8.11 17.82L6.72 19.90L4.10 17.28L6.18 15.89L5.13 13.37L2.68 13.85L2.68 10.15L5.13 10.63L6.18 8.11L4.10 6.72L6.72 4.10L8.11 6.18L10.63 5.13L10.15 2.68L13.85 2.68L13.37 5.13L15.89 6.18L17.28 4.10L19.90 6.72L17.82 8.11L18.87 10.63Z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6Z"),
    Track("M8 15a3 3 0 1 0 0 6a3 3 0 1 0 0-6Z", "M17 12a3 3 0 1 0 0 6a3 3 0 1 0 0-6Z", "M11 18V5l9-2v12"),
    Undo("M4 5v6h6", "M4.6 11A7.5 7.5 0 1 1 6.4 17"),
    Unfollow("M10 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8Z", "M3 21a7 7 0 0 1 14 0", "M16 9h6"),
    Unlink("M8.5 11.5 6 14a3.5 3.5 0 0 0 5 5l2.5-2.5M15.5 12.5 18 10a3.5 3.5 0 0 0-5-5l-2.5 2.5", "M4 4l3 3M17 17l3 3"),
    Upload("M12 15V3M6.5 8.5 12 3l5.5 5.5M4 20h16"),
    Volume("M3 9h4l5-4v14l-5-4H3Z", "M16 9a4 4 0 0 1 0 6M18.5 6a8 8 0 0 1 0 12"),
    Waiting("M7 3h10l-5 9 5 9H7l5-9Z", "M5 3h14M5 21h14"),
    Want("M6 3h12v18l-6-4.5L6 21Z", "M12 7v6M9 10h6"),
    ;

    val vector: ImageVector by lazy {
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            )
        }
        builder.build()
    }
}

/** An icon beside text is decoration and says nothing; an icon that is the
 * whole control gets its name from the control, so pass `description` only
 * for an icon that stands alone and is not inside a labelled control. */
@Composable
fun MisprintIcon(icon: Misprint, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = LocalContentColor.current, description: String? = null) {
    Icon(icon.vector, contentDescription = description, modifier = modifier.size(size), tint = tint)
}
