package com.example.newsly.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.newsly.data.Article
import com.example.newsly.ui.components.FloatingIconButton
import com.example.newsly.ui.components.NewslyIcons
import com.example.newsly.ui.components.PrimaryButton
import com.example.newsly.ui.components.PublisherLogo
import com.example.newsly.ui.components.StoryImage
import com.example.newsly.ui.theme.Newsly
import com.example.newsly.util.shareArticle
import java.net.URI

/**
 * In-app preview of a story: photo, headline and the summary the publisher puts
 * in its feed. The full text stays on the publisher's page ("Read the full story").
 */
@Composable
fun ArticleDetailScreen(
    article: Article,
    saved: Boolean,
    onBack: () -> Unit,
    onReadFull: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val context = LocalContext.current
    val hasPhoto = article.imageUrl != null
    Box(Modifier.fillMaxSize().background(Newsly.colors.surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (hasPhoto) {
                StoryImage(article.imageUrl, Modifier.fillMaxWidth().height(380.dp))
            } else {
                Spacer(Modifier.statusBarsPadding().height(72.dp))
            }
            // The text sheet slides up over the bottom of the photo.
            Column(
                Modifier
                    .offset(y = if (hasPhoto) (-30).dp else 0.dp)
                    .background(Newsly.colors.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 120.dp),
            ) {
                article.publishedAt?.let {
                    Text(
                        DateUtils.formatDateTime(
                            context, it.toEpochMilli(),
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Newsly.colors.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(article.title, style = MaterialTheme.typography.headlineLarge, color = Newsly.colors.ink)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PublisherLogo(article, 32.dp, corner = 9.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(article.sourceName, style = MaterialTheme.typography.titleSmall, color = Newsly.colors.ink)
                        runCatching { URI(article.url).host?.removePrefix("www.") }.getOrNull()
                            ?.takeUnless { it == "news.google.com" }
                            ?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Newsly.colors.muted) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Newsly.colors.line.copy(alpha = 0.6f))
                article.description?.let {
                    Spacer(Modifier.height(18.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Newsly.colors.ink.copy(alpha = 0.9f))
                }
            }
        }

        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
            FloatingIconButton(NewslyIcons.Back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            FloatingIconButton(NewslyIcons.Share, "Share", { context.shareArticle(article.title, article.url) })
            Spacer(Modifier.width(10.dp))
            FloatingIconButton(
                if (saved) BookmarkFilled else NewslyIcons.Bookmark,
                if (saved) "Remove bookmark" else "Save story",
                onToggleBookmark,
                filled = saved,
            )
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Newsly.colors.surface)) {
            HorizontalDivider(color = Newsly.colors.line.copy(alpha = 0.6f))
            PrimaryButton(
                "Read the full story on ${article.sourceName}",
                onReadFull,
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}
