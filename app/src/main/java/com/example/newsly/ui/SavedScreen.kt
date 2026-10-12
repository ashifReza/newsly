package com.example.newsly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.newsly.data.Article
import com.example.newsly.ui.components.NewslyIcons
import com.example.newsly.ui.components.PublisherLogo
import com.example.newsly.ui.components.StoryImage
import com.example.newsly.ui.components.shortAge
import com.example.newsly.ui.theme.Newsly

@Composable
fun SavedScreen(
    bookmarks: List<Article>?,
    onArticleClick: (Article) -> Unit,
    onRemove: (Article) -> Unit,
) {
    val c = Newsly.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Saved", style = MaterialTheme.typography.displaySmall, color = c.ink)
            if (!bookmarks.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(NewslyIcons.CheckCircle, null, tint = if (c.isDark) Color(0xFF5FD3A6) else Color(0xFF0E7A55), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${bookmarks.size} ${if (bookmarks.size == 1) "story" else "stories"} · readable offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                }
            }
        }
        when {
            bookmarks == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            bookmarks.isEmpty() -> EmptySaved()
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(bookmarks, key = { it.url }) { article ->
                    SavedCard(article, { onArticleClick(article) }, { onRemove(article) }, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun SavedCard(article: Article, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    val c = Newsly.colors
    val textCard = article.imageUrl == null
    val bg = if (textCard) c.strong else c.surface
    val fg = if (textCard) c.onStrong else c.ink
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = bg, modifier = modifier.fillMaxWidth()) {
        Box {
            Column {
                if (textCard) {
                    Box(Modifier.padding(start = 14.dp, top = 16.dp)) { PublisherLogo(article, 36.dp, corner = 10.dp, onDark = !c.isDark) }
                } else {
                    StoryImage(article.imageUrl, Modifier.fillMaxWidth().height(120.dp))
                }
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!textCard) Meta(article, fg)
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.95f),
                        color = fg,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (textCard) Meta(article, fg)
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp)) {
                Box(Modifier.size(34.dp).background(if (textCard) fg.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.92f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(BookmarkFilled, "Remove bookmark", tint = c.accent, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun Meta(article: Article, fg: Color) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = fg)) { append(article.sourceName) }
            article.publishedAt?.let { append(" · ${it.shortAge(suffix = false)}") }
        },
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
        color = fg.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptySaved() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(NewslyIcons.Bookmark, null, tint = Newsly.colors.muted, modifier = Modifier.size(44.dp))
        Text("Nothing saved yet", style = MaterialTheme.typography.titleLarge, color = Newsly.colors.ink)
        Text(
            "Tap the bookmark on any story to keep it here, even offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = Newsly.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}
