package com.example.newsly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.newsly.R
import com.example.newsly.data.Accent
import com.example.newsly.data.Settings
import com.example.newsly.data.TextSize
import com.example.newsly.data.ThemeMode

/** The design's palette. Material's colour roles are filled from it, but screens mostly read these directly. */
@Immutable
data class NewslyColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    /** Secondary text; meets 4.5:1 on [surface] and [background]. */
    val muted: Color,
    val line: Color,
    /** Track behind pill toggles. */
    val soft: Color,
    val accent: Color,
    /** The accent darkened (light mode) or lightened (dark mode) enough for small text. */
    val accentText: Color,
    /** Selected chips, the bottom-bar pill and primary buttons: ink in light mode, near-white in dark. */
    val strong: Color,
    val onStrong: Color,
    /** Grey stand-in behind photos while they load. */
    val placeholder: Color,
    val isDark: Boolean,
)

private data class AccentColors(val fill: Color, val lightText: Color, val darkText: Color)

fun Accent.color(): Color = accentColors(this).fill

private fun accentColors(accent: Accent) = when (accent) {
    Accent.EMBER -> AccentColors(Color(0xFFE8441A), Color(0xFFB4380F), Color(0xFFFF8A63))
    Accent.COBALT -> AccentColors(Color(0xFF2F6BFF), Color(0xFF1F4FCC), Color(0xFF8AB0FF))
    Accent.JADE -> AccentColors(Color(0xFF0E9F6E), Color(0xFF0A6E4C), Color(0xFF5FD3A6))
    Accent.VIOLET -> AccentColors(Color(0xFF7C3AED), Color(0xFF6227C9), Color(0xFFB79BFF))
    Accent.SAFFRON -> AccentColors(Color(0xFFD99400), Color(0xFF8A5E00), Color(0xFFF2C25B))
}

fun newslyColors(dark: Boolean, accent: Accent): NewslyColors {
    val a = accentColors(accent)
    return if (dark) {
        NewslyColors(
            background = Color(0xFF0E0E0F), surface = Color(0xFF1A1A1C), ink = Color(0xFFF3F2EE),
            muted = Color(0xFFB9B7B1), line = Color(0xFF34343A), soft = Color(0xFF1D1D20),
            accent = a.fill, accentText = a.darkText, strong = Color(0xFFF3F2EE), onStrong = Color(0xFF121212),
            placeholder = Color(0xFF2A2A2D), isDark = true,
        )
    } else {
        NewslyColors(
            background = Color(0xFFF6F5F2), surface = Color(0xFFFFFFFF), ink = Color(0xFF121212),
            muted = Color(0xFF4A4945), line = Color(0xFFDAD8D1), soft = Color(0xFFE9E7E1),
            accent = a.fill, accentText = a.lightText, strong = Color(0xFF121212), onStrong = Color(0xFFFFFFFF),
            placeholder = Color(0xFFE2DFD8), isDark = false,
        )
    }
}

val LocalNewslyColors = staticCompositionLocalOf { newslyColors(dark = false, accent = Accent.EMBER) }

/** Shortcut: `Newsly.colors.ink`. */
object Newsly {
    val colors: NewslyColors
        @Composable get() = LocalNewslyColors.current
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, vararg extra: FontVariation.Setting) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

/** Bricolage's optical size for headline-sized text (its axis runs 12–96). */
@OptIn(ExperimentalTextApi::class)
private val headlineOpsz = FontVariation.Setting("opsz", 24f)

/** Headlines: Bricolage Grotesque. */
val DisplayFont = FontFamily(
    variable(R.font.bricolage_grotesque, 500, headlineOpsz),
    variable(R.font.bricolage_grotesque, 600, headlineOpsz),
    variable(R.font.bricolage_grotesque, 700, headlineOpsz),
    variable(R.font.bricolage_grotesque, 800, headlineOpsz),
)

/** Everything else: Figtree. */
val BodyFont = FontFamily(
    variable(R.font.figtree, 400),
    variable(R.font.figtree, 500),
    variable(R.font.figtree, 600),
    variable(R.font.figtree, 700),
)

private fun display(size: Int, weight: Int, line: Double, tracking: Double) = TextStyle(
    fontFamily = DisplayFont, fontWeight = FontWeight(weight), fontSize = size.sp,
    lineHeight = (size * line).sp, letterSpacing = tracking.em,
)

private fun body(size: Int, weight: Int, line: Double = 1.4) = TextStyle(
    fontFamily = BodyFont, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = (size * line).sp,
)

val NewslyTypography = Typography(
    displaySmall = display(34, 800, 1.0, -0.025),
    headlineLarge = display(30, 700, 1.1, -0.015),
    headlineMedium = display(25, 700, 1.14, -0.012),
    headlineSmall = display(21, 700, 1.2, -0.01),
    titleLarge = display(19, 700, 1.2, -0.008),
    titleMedium = display(17, 600, 1.24, 0.0),
    titleSmall = body(14, 700),
    bodyLarge = body(17, 400, 1.55),
    bodyMedium = body(15, 400, 1.45),
    bodySmall = body(13, 400),
    labelLarge = body(15, 700),
    labelMedium = body(13, 600),
    labelSmall = body(12, 600),
)

@Composable
fun NewslyTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = newslyColors(dark, settings.accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onStrong, background = colors.background,
            onBackground = colors.ink, surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.soft, onSurfaceVariant = colors.muted, outline = colors.line,
            inverseSurface = colors.ink, inverseOnSurface = colors.surface, inversePrimary = colors.accentText,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onStrong, background = colors.background,
            onBackground = colors.ink, surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.soft, onSurfaceVariant = colors.muted, outline = colors.line,
            inverseSurface = colors.ink, inverseOnSurface = colors.surface, inversePrimary = colors.accent,
        )
    }
    // "Large" text scales on top of the phone's own font size setting.
    val density = LocalDensity.current
    val scale = if (settings.textSize == TextSize.LARGE) 1.12f else 1f
    CompositionLocalProvider(
        LocalNewslyColors provides colors,
        LocalDensity provides Density(density.density, density.fontScale * scale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = NewslyTypography, content = content)
    }
}
