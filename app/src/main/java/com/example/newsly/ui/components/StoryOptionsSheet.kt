package com.example.newsly.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.newsly.data.Article
import com.example.newsly.ui.BookmarkFilled
import com.example.newsly.ui.theme.Newsly
import com.example.newsly.util.openArticle
import com.example.newsly.util.shareArticle

/** Long-press menu for a story: share, save, copy link, open in browser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryOptionsSheet(article: Article, saved: Boolean, onToggleBookmark: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = Newsly.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceMeta(article)
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = c.line.copy(alpha = 0.6f))
            Spacer(Modifier.height(6.dp))
            Option(NewslyIcons.Share, "Share") {
                onDismiss()
                context.shareArticle(article.title, article.url)
            }
            Option(if (saved) BookmarkFilled else NewslyIcons.Bookmark, if (saved) "Remove from Saved" else "Save for later") {
                onDismiss()
                onToggleBookmark()
            }
            Option(NewslyIcons.Link, "Copy link") {
                onDismiss()
                context.copyLink(article.url)
            }
            Option(NewslyIcons.Globe, "Open in browser") {
                onDismiss()
                context.openArticle(article.url)
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Newsly.colors.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize), color = Newsly.colors.ink)
    }
}

private fun Context.copyLink(url: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Story link", url))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
    }
}
