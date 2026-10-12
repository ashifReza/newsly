package com.example.newsly.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.newsly.data.Article
import com.example.newsly.ui.BookmarkFilled
import com.example.newsly.ui.theme.Newsly
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Newsly." with the accent dot. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Newsly")
            withStyle(SpanStyle(color = Newsly.colors.accent)) { append(".") }
        },
        style = MaterialTheme.typography.displaySmall,
        color = Newsly.colors.ink,
        modifier = modifier.semantics { contentDescription = "Newsly" },
    )
}

/** "7 min ago", "3 hr ago", "Mon". */
fun Instant.shortAge(now: Instant = Instant.now(), suffix: Boolean = true): String {
    val d = Duration.between(this, now)
    val ago = if (suffix) " ago" else ""
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min$ago"
        d.toHours() < 24 -> "${d.toHours()} hr$ago"
        else -> DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(atZone(ZoneId.systemDefault()))
    }
}

/** Story photo, with a flat placeholder while it loads or if it fails. */
@Composable
fun StoryImage(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(0.dp)) {
    val placeholder = @Composable { Box(Modifier.background(Newsly.colors.placeholder)) }
    Box(modifier.clip(shape).background(Newsly.colors.placeholder)) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Publisher logo in a rounded square; initials if the logo can't load. */
@Composable
fun PublisherLogo(article: Article, size: Dp, corner: Dp = size / 4, onDark: Boolean = false) {
    val shape = RoundedCornerShape(corner)
    val initials = article.sourceName.split(' ', '.', '-').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "N" }
    val tileBg = if (onDark) Color.White else Newsly.colors.soft
    val fallback = @Composable {
        Box(Modifier.size(size).background(tileBg, shape), contentAlignment = Alignment.Center) {
            Text(
                initials,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (onDark) Color(0xFF121212) else Newsly.colors.ink,
            )
        }
    }
    if (article.logoUrl == null) {
        fallback()
    } else {
        SubcomposeAsyncImage(
            model = article.logoUrl,
            contentDescription = null,
            loading = { fallback() },
            error = { fallback() },
            modifier = Modifier.size(size).clip(shape).background(tileBg),
        )
    }
}

/** Logo, publisher and age on one line: "[G] The Guardian · 11 min". */
@Composable
fun SourceMeta(article: Article, modifier: Modifier = Modifier, logoSize: Dp = 18.dp, longAge: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PublisherLogo(article, logoSize, corner = 5.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Newsly.colors.ink)) { append(article.sourceName) }
                article.publishedAt?.let { append(" · ${it.shortAge(suffix = longAge)}") }
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            color = Newsly.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BookmarkToggle(saved: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Newsly.colors.ink) {
    IconButton(onClick = onClick, modifier = modifier.size(44.dp)) {
        Icon(
            imageVector = if (saved) BookmarkFilled else NewslyIcons.Bookmark,
            contentDescription = if (saved) "Remove bookmark" else "Save story",
            tint = if (saved) Newsly.colors.accent else tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

/** What a story card can do. Built per story by the screen that shows it. */
class StoryActions(
    val open: () -> Unit,
    val toggleBookmark: () -> Unit,
    val share: () -> Unit,
    /** Long-press: the story options sheet. */
    val more: () -> Unit,
)

/** A card that opens its story on tap and shows story options on long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorySurface(
    actions: StoryActions,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    shadow: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(shape = shape, color = color, shadowElevation = shadow, modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier.combinedClickable(
                onClickLabel = "Open story",
                onLongClickLabel = "Story options",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.more()
                },
                onClick = actions.open,
            ),
        ) { content() }
    }
}

@Composable
fun ShareButton(onClick: () -> Unit, tint: Color = Newsly.colors.ink) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(NewslyIcons.Share, contentDescription = "Share story", tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** The big lead story with photo. */
@Composable
fun HeroCard(article: Article, saved: Boolean, actions: StoryActions, modifier: Modifier = Modifier) {
    StorySurface(
        actions,
        shape = RoundedCornerShape(26.dp),
        color = Newsly.colors.surface,
        shadow = if (Newsly.colors.isDark) 0.dp else 3.dp,
        modifier = modifier,
    ) {
        Column {
            StoryImage(article.imageUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9.4f))
            Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceMeta(article, Modifier.weight(1f), logoSize = 22.dp)
                    ShareButton(actions.share)
                    BookmarkToggle(saved, actions.toggleBookmark)
                }
                Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(article.title, style = MaterialTheme.typography.headlineMedium, color = Newsly.colors.ink)
                    article.description?.let {
                        Text(
                            it.replace(Regex("\\s+"), " "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Newsly.colors.muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Lead story without a photo: a bold ink card. */
@Composable
fun LeadTextCard(article: Article, saved: Boolean, actions: StoryActions, scope: String?) {
    val bg = Newsly.colors.strong
    val fg = Newsly.colors.onStrong
    StorySurface(actions, shape = RoundedCornerShape(26.dp), color = bg) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PublisherLogo(article, 36.dp, corner = 10.dp, onDark = !Newsly.colors.isDark)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(article.sourceName, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
                    Text(
                        listOfNotNull(article.publishedAt?.shortAge(), scope).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = fg.copy(alpha = 0.72f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        ShareButton(actions.share, tint = fg)
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        BookmarkToggle(saved, actions.toggleBookmark, tint = fg)
                    }
                }
            }
            Text(article.title, style = MaterialTheme.typography.headlineMedium, color = fg)
        }
    }
}

/** A story row with a square thumbnail on the right. */
@Composable
fun CompactRow(article: Article, saved: Boolean, actions: StoryActions) {
    StorySurface(actions, shape = RoundedCornerShape(20.dp), color = Newsly.colors.surface) {
        Row(Modifier.padding(start = 14.dp, top = 4.dp, bottom = 14.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceMeta(article, Modifier.weight(1f), logoSize = 16.dp, longAge = false)
                    BookmarkToggle(saved, actions.toggleBookmark)
                }
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Newsly.colors.ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (article.imageUrl != null) {
                StoryImage(article.imageUrl, Modifier.padding(top = 10.dp).size(92.dp), RoundedCornerShape(14.dp))
            }
        }
    }
}

/** Where a row sits in a grouped list, for its rounded corners. */
enum class GroupPosition { ONLY, FIRST, MIDDLE, LAST }

fun groupPosition(index: Int, count: Int) = when {
    count == 1 -> GroupPosition.ONLY
    index == 0 -> GroupPosition.FIRST
    index == count - 1 -> GroupPosition.LAST
    else -> GroupPosition.MIDDLE
}

/** A text story inside a white grouped list, with a publisher badge. */
@Composable
fun GroupedRow(article: Article, position: GroupPosition, saved: Boolean, actions: StoryActions) {
    val r = 22.dp
    val shape = when (position) {
        GroupPosition.ONLY -> RoundedCornerShape(r)
        GroupPosition.FIRST -> RoundedCornerShape(topStart = r, topEnd = r)
        GroupPosition.LAST -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        GroupPosition.MIDDLE -> RoundedCornerShape(0.dp)
    }
    StorySurface(actions, shape = shape, color = Newsly.colors.surface) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.padding(top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PublisherLogo(article, 40.dp, corner = 12.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(article.title, style = MaterialTheme.typography.titleMedium, color = Newsly.colors.ink)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Newsly.colors.ink)) { append(article.sourceName) }
                            article.publishedAt?.let { append(" · ${it.shortAge(suffix = false)}") }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = Newsly.colors.muted,
                    )
                }
                BookmarkToggle(saved, actions.toggleBookmark)
            }
            if (position == GroupPosition.FIRST || position == GroupPosition.MIDDLE) {
                HorizontalDivider(color = Newsly.colors.line.copy(alpha = 0.55f), thickness = 1.dp)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Newsly.colors.ink, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), color = Newsly.colors.muted)
        }
    }
}

/** Small accent-dot label, e.g. "TOP STORY". */
@Composable
fun Kicker(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(Newsly.colors.accent, CircleShape))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = MaterialTheme.typography.labelSmall.fontSize * 0.08f),
            color = Newsly.colors.accentText,
        )
    }
}

/** Two-option pill toggle (World / Near you). */
@Composable
fun PillToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (Int) -> ImageVector? = { null },
) {
    Row(
        modifier.fillMaxWidth().background(Newsly.colors.soft, RoundedCornerShape(999.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(999.dp)) else Modifier)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) (if (Newsly.colors.isDark) Color(0xFF34343A) else Newsly.colors.surface) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                leadingIcon(i)?.let {
                    Icon(it, null, tint = if (on) Newsly.colors.accent else Newsly.colors.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (on) Newsly.colors.ink else Newsly.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, height: Dp = 40.dp) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .heightIn(min = height)
            .clip(shape)
            .then(
                if (selected) Modifier.background(Newsly.colors.strong)
                else Modifier.background(Newsly.colors.surface.copy(alpha = 0f)).border(1.dp, Newsly.colors.line, shape)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) Newsly.colors.onStrong else Newsly.colors.ink.copy(alpha = 0.82f),
        )
    }
}

/** Tinted banner with an icon, two lines of text and an optional action. */
@Composable
fun InfoBanner(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = Newsly.colors
    val tint = c.accent.copy(alpha = if (c.isDark) 0.16f else 0.10f)
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint, RoundedCornerShape(18.dp))
            .border(1.dp, c.accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = c.accentText, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (action != null) {
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(999.dp)).background(c.accent)
                    .clickable(role = Role.Button, onClick = onAction).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = Color(0xFF121212))
            }
        }
    }
}

/** Big pill button: ink in light mode, near-white in dark. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: ImageVector? = NewslyIcons.ArrowRight) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Newsly.colors.strong)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize), color = Newsly.colors.onStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Icon(trailing, null, tint = Newsly.colors.onStrong, modifier = Modifier.size(18.dp))
        }
    }
}

/** Round icon button floating over photos. */
@Composable
fun FloatingIconButton(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) Newsly.colors.accent else Color.White.copy(alpha = 0.92f),
        contentColor = if (filled) Color.White else Color(0xFF121212),
        shadowElevation = 4.dp,
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)
