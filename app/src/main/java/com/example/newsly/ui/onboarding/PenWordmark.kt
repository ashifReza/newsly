package com.example.newsly.ui.onboarding

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** True when the user has turned animations off in the phone's settings. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * "Newsly" written out as if by a pen: the script letters' outlines are traced
 * left to right, then the ink fills in and the accent dot lands.
 * [onFinished] runs once the writing is complete.
 */
@Composable
fun PenWordmark(
    ink: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    reducedOverride: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion() || reducedOverride
    val typeface = remember {
        runCatching {
            Typeface.Builder(context.assets, "fonts/dancing_script.ttf")
                .setFontVariationSettings("'wght' 700")
                .build()
        }.getOrDefault(Typeface.SERIF)
    }
    val write = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val fill = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val dot = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            write.animateTo(1f, tween(2300, delayMillis = 250, easing = LinearEasing))
            coroutineScope {
                launch { fill.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
                launch { dot.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            }
        }
        onFinished()
    }

    Canvas(modifier.semantics { contentDescription = "Newsly" }) {
        val textSize = size.height * 0.78f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
        }
        val text = "Newsly"
        val width = paint.measureText(text)
        val dotRadius = textSize * 0.085f
        val startX = (size.width - width - dotRadius * 3f) / 2f
        val baseline = size.height * 0.74f

        val outline = Path()
        paint.getTextPath(text, 0, text.length, startX, baseline, outline)

        // Split the outline into its separate strokes so they can be drawn one after another.
        val measure = PathMeasure(outline, false)
        val pieces = mutableListOf<Pair<Path, Float>>()
        do {
            val piece = Path()
            if (measure.length > 0f && measure.getSegment(0f, measure.length, piece, true)) pieces += piece to measure.length
        } while (measure.nextContour())
        val total = pieces.sumOf { it.second.toDouble() }.toFloat()

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.018f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = ink.toArgb()
        }
        val filler = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ink.copy(alpha = fill.value).toArgb()
        }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (fill.value > 0f) native.drawPath(outline, filler)
            var remaining = write.value * total
            for ((piece, length) in pieces) {
                if (remaining <= 0f) break
                if (remaining >= length) {
                    native.drawPath(piece, stroke)
                } else {
                    val partial = Path()
                    PathMeasure(piece, false).getSegment(0f, remaining, partial, true)
                    native.drawPath(partial, stroke)
                }
                remaining -= length
            }
            if (dot.value > 0f) {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent.toArgb() }
                native.drawCircle(startX + width + dotRadius * 1.6f, baseline - dotRadius, dotRadius * dot.value, dotPaint)
            }
        }
    }
}
