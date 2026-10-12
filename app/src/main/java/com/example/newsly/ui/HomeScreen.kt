package com.example.newsly.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newsly.data.Article
import com.example.newsly.data.Category
import com.example.newsly.ui.components.Chip
import com.example.newsly.ui.components.CompactRow
import com.example.newsly.ui.components.GroupedRow
import com.example.newsly.ui.components.HeroCard
import com.example.newsly.ui.components.InfoBanner
import com.example.newsly.ui.components.Kicker
import com.example.newsly.ui.components.LeadTextCard
import com.example.newsly.ui.components.NewslyIcons
import com.example.newsly.ui.components.PillToggle
import com.example.newsly.ui.components.PrimaryButton
import com.example.newsly.ui.components.SectionHeader
import com.example.newsly.ui.components.Wordmark
import com.example.newsly.ui.components.groupPosition
import com.example.newsly.ui.components.shortAge
import com.example.newsly.ui.theme.Newsly
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: NewsViewModel, onArticleClick: (Article) -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedUrls by viewModel.savedUrls.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onLocationPermissionResult() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault())).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelSmall.fontSize * 0.08f),
                    color = Newsly.colors.muted,
                )
                Wordmark()
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(NewslyIcons.Tune, contentDescription = "Appearance settings", tint = Newsly.colors.ink)
            }
        }

        PillToggle(
            options = listOf("World", state.localScope?.let { "Near you · $it" } ?: "Near you"),
            selected = selectedTab,
            onSelect = { selectedTab = it },
            leadingIcon = { if (it == 1) NewslyIcons.Pin else null },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Category.entries) { category ->
                Chip(category.label, category == state.category, onClick = { viewModel.selectCategory(category) })
            }
        }

        val local = selectedTab == 1
        // Keyed so World and Near you each keep their own scroll position.
        key(local) { Feed(
            feed = if (local) state.local else state.world,
            refreshing = if (local) state.localRefreshing else state.worldRefreshing,
            onRefresh = if (local) viewModel::refreshLocal else viewModel::refreshWorld,
            onArticleClick = onArticleClick,
            savedUrls = savedUrls,
            onToggleBookmark = viewModel::toggleBookmark,
            scope = if (local) state.localScope else null,
            topBanner = if (local && state.location?.isApproximate == true && !viewModel.hasLocationPermission) {
                {
                    InfoBanner(
                        icon = NewslyIcons.Pin,
                        title = "Showing ${state.location?.countryName ?: "your region"}",
                        body = "Allow location for news from your city.",
                        action = "Allow",
                        onAction = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    )
                }
            } else null,
        ) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feed(
    feed: FeedState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleClick: (Article) -> Unit,
    savedUrls: Set<String>,
    onToggleBookmark: (Article) -> Unit,
    scope: String?,
    topBanner: (@Composable () -> Unit)?,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when (feed) {
            FeedState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Newsly.colors.accent)
            }
            is FeedState.Error -> CenteredMessage(feed.message, actionLabel = "Try again", onAction = onRefresh)
            is FeedState.Success -> if (feed.articles.isEmpty()) {
                CenteredMessage("No stories right now.", actionLabel = "Refresh", onAction = onRefresh)
            } else {
                val listState = rememberLazyListState()
                // A refresh can add stories (or the offline banner) above the first one on screen.
                // The list normally holds its place, hiding them; if the reader was at the top, keep them there.
                // This is read during composition, before the list lays out the new content.
                val wasAtTop = remember(feed.articles, feed.refreshProblem) {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                }
                LaunchedEffect(feed.articles, feed.refreshProblem) {
                    if (wasAtTop) listState.scrollToItem(0)
                }
                val lead = feed.articles.first()
                val rest = feed.articles.drop(1)
                val photoLed = lead.imageUrl != null
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(if (photoLed) 12.dp else 0.dp),
                ) {
                    topBanner?.let { banner -> item(key = "banner") { Box(Modifier.padding(bottom = 12.dp)) { banner() } } }
                    feed.refreshProblem?.let { problem ->
                        item(key = "refresh-problem") {
                            Box(Modifier.padding(bottom = 12.dp)) {
                                InfoBanner(
                                    icon = NewslyIcons.WifiOff,
                                    title = if (problem == RefreshProblem.OFFLINE) "You’re offline" else "Couldn’t refresh",
                                    body = feed.fetchedAt?.let { "Showing stories saved ${it.shortAge()}" } ?: "Showing saved stories",
                                    action = "Retry",
                                    onAction = onRefresh,
                                )
                            }
                        }
                    }
                    if (photoLed) {
                        item(key = "kicker") { Kicker("Top story") }
                        item(key = lead.url) {
                            HeroCard(lead, lead.url in savedUrls, { onArticleClick(lead) }, { onToggleBookmark(lead) })
                        }
                        if (rest.isNotEmpty()) item(key = "latest") { SectionHeader("Latest", "${feed.articles.size} stories") }
                        items(rest, key = { it.url }) { article ->
                            CompactRow(article, article.url in savedUrls, { onArticleClick(article) }, { onToggleBookmark(article) })
                        }
                    } else {
                        item(key = lead.url) {
                            LeadTextCard(lead, lead.url in savedUrls, { onArticleClick(lead) }, { onToggleBookmark(lead) }, scope)
                        }
                        if (rest.isNotEmpty()) {
                            item(key = "latest") {
                                SectionHeader(scope?.let { "Around $it" } ?: "Latest", "Local outlets", Modifier.padding(top = 14.dp, bottom = 12.dp))
                            }
                        }
                        itemsIndexed(rest, key = { _, a -> a.url }) { i, article ->
                            GroupedRow(
                                article = article,
                                position = groupPosition(i, rest.size),
                                saved = article.url in savedUrls,
                                onClick = { onArticleClick(article) },
                                onToggleBookmark = { onToggleBookmark(article) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Scrollable so pull-to-refresh still works on empty and error states. */
@Composable
fun CenteredMessage(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))
        Text(message, style = MaterialTheme.typography.titleLarge, color = Newsly.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(actionLabel, onAction, Modifier.fillMaxWidth(0.6f), trailing = null)
    }
}
