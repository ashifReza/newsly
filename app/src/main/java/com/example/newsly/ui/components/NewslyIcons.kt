package com.example.newsly.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn to match the design (24-unit grid, 2-unit round strokes). */
object NewslyIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 2f) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    val Home = stroke("Home", "M4 5h11v14H6a2 2 0 0 1-2-2z", "M15 9h5v8a2 2 0 0 1-2 2h-3", "M7.5 9h4M7.5 12.5h4M7.5 16h4")
    val Search = stroke("Search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-3.5-3.5")
    val Bookmark = stroke("Bookmark", "M6 3h12v18l-6-4-6 4z")
    val Pin = stroke("Pin", "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", "M12 7a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z")
    val ArrowRight = stroke("ArrowRight", "M5 12h14", "M13 6l6 6-6 6", width = 2.2f)
    val Back = stroke("Back", "M15 5l-7 7 7 7", width = 2.2f)
    val Share = stroke("Share", "M12 3v12", "M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6")
    val Close = stroke("Close", "M6 6l12 12M18 6L6 18", width = 2.4f)
    val Tune = stroke("Tune", "M4 7h10", "M18 7h2", "M4 17h4", "M12 17h8", "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    val CheckCircle = stroke("CheckCircle", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M8 12.5l2.7 2.7L16 10", width = 2.2f)
    val Link = stroke("Link", "M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1.2 1.2", "M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1.2-1.2")
    val ArrowUp = stroke("ArrowUp", "M12 19V5", "M6 11l6-6 6 6", width = 2.2f)
    val Globe = stroke("Globe", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M3 12h18", "M12 3c2.5 2.7 3.5 5.7 3.5 9s-1 6.3-3.5 9c-2.5-2.7-3.5-5.7-3.5-9s1-6.3 3.5-9z")
    val WifiOff = stroke(
        "WifiOff", "M2 2l20 20", "M8.5 16.5a5 5 0 0 1 7 0", "M5 12.6a10 10 0 0 1 5.2-2.5",
        "M19 12.6a10 10 0 0 0-2.6-1.8", "M2 8.8a15 15 0 0 1 4.2-2.6", "M22 8.8A15 15 0 0 0 11 5", "M12 20h.01",
    )
}
