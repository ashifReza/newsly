package com.example.newsly.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.example.newsly.data.Article
import com.example.newsly.ui.components.BookmarkToggle
import com.example.newsly.ui.components.NewslyIcons
import com.example.newsly.ui.theme.Newsly
import androidx.compose.material3.TopAppBarDefaults
import com.example.newsly.util.openArticle
import com.example.newsly.util.shareArticle

/**
 * The publisher's page shown inside Newsly, exactly as the publisher serves it
 * (including its own ads and paywall), with Newsly's toolbar on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleWebScreen(
    article: Article,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentUrl by remember { mutableStateOf(article.url) }
    var menuOpen by remember { mutableStateOf(false) }

    fun goBack() {
        val view = webView
        if (view != null && view.canGoBackToArticle()) view.goBack() else onBack()
    }
    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(article.sourceName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Uri.parse(currentUrl).host?.removePrefix("www.").orEmpty(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::goBack) {
                        Icon(NewslyIcons.Back, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Newsly.colors.surface),
                actions = {
                    BookmarkToggle(saved = saved, onClick = onToggleBookmark)
                    IconButton(onClick = { context.shareArticle(article.title, currentUrl) }) {
                        Icon(NewslyIcons.Share, contentDescription = "Share")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Open in browser") },
                                onClick = {
                                    menuOpen = false
                                    context.openArticle(currentUrl)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Reload") },
                                onClick = {
                                    menuOpen = false
                                    webView?.reload()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        configure(
                            onProgress = { progress = it },
                            onPageStarted = { currentUrl = it },
                        )
                        loadUrl(article.url)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
            if (progress < 1f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Newsly.colors.accent, trackColor = Newsly.colors.soft)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled") // News sites need JavaScript; no JS bridge is exposed.
private fun WebView.configure(onProgress: (Float) -> Unit, onPageStarted: (String) -> Unit) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        loadWithOverviewMode = true
        useWideViewPort = true
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress / 100f)
    }
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onPageStarted(url)

        /** Web links stay in the app; anything else (mailto:, intent:, app links) goes to the system. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "http" || uri.scheme == "https") return false
            runCatching {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return true
        }
    }
}

/**
 * Google News links open a Google page that redirects to the publisher. Going
 * back to it would just redirect forward again, so treat it as the start.
 */
private fun WebView.canGoBackToArticle(): Boolean {
    if (!canGoBack()) return false
    val history = copyBackForwardList()
    val previous = history.getItemAtIndex(history.currentIndex - 1)?.url.orEmpty()
    return !previous.startsWith("https://news.google.com/")
}
