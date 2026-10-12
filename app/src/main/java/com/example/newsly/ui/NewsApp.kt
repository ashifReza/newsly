package com.example.newsly.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.newsly.data.Article
import com.example.newsly.ui.components.StoryActions
import com.example.newsly.ui.components.StoryOptionsSheet
import com.example.newsly.util.shareArticle
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newsly.data.AppSettings
import com.example.newsly.ui.components.NewslyIcons
import com.example.newsly.ui.onboarding.ThemeChooser
import com.example.newsly.ui.theme.Newsly

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", NewslyIcons.Home),
    SEARCH("Search", NewslyIcons.Search),
    SAVED("Saved", NewslyIcons.Bookmark),
}

/** Home / Search / Saved tabs with a bottom bar; stories and settings open on top. */
@Composable
fun NewsApp(viewModel: NewsViewModel, appSettings: AppSettings) {
    val screens by viewModel.screens.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val savedUrls by viewModel.savedUrls.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearchList.collectAsStateWithLifecycle()
    val settings by appSettings.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    val context = LocalContext.current
    var optionsFor by remember { mutableStateOf<Article?>(null) }
    val tabState = rememberSaveableStateHolder()

    fun actionsFor(article: Article, onOpen: () -> Unit = { viewModel.openArticle(article) }) = StoryActions(
        open = onOpen,
        toggleBookmark = { viewModel.toggleBookmark(article) },
        share = { context.shareArticle(article.title, article.url) },
        more = { optionsFor = article },
    )

    // On returning to the app: check whether the user has moved city. Then, and every minute
    // while the app is on screen, refresh feeds older than 10 minutes.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.checkLocationChange()
            while (true) {
                viewModel.refreshIfStale()
                delay(60_000)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.bookmarkEvents.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                BookmarkEvent.Saved -> snackbarHostState.showSnackbar("Saved to bookmarks", duration = SnackbarDuration.Short)
                is BookmarkEvent.Removed -> {
                    val result = snackbarHostState.showSnackbar(
                        "Removed from bookmarks",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(event.bookmark)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Newsly.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                // Each tab keeps its own state (World/Near you, scroll positions) while another is shown.
                tabState.SaveableStateProvider(tab.name) { when (tab) {
                    Tab.HOME -> HomeScreen(viewModel, storyActions = { actionsFor(it) }, onOpenSettings = viewModel::openSettings)
                    Tab.SEARCH -> SearchScreen(
                        state = searchState,
                        recentSearches = recentSearches,
                        savedUrls = savedUrls,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onSearch = { viewModel.search(it) },
                        onRetry = { viewModel.search(searchState.searchedQuery.orEmpty(), forceRefresh = true) },
                        onClear = viewModel::clearSearch,
                        onClearRecent = viewModel::clearRecentSearches,
                        storyActions = { article ->
                            actionsFor(article, onOpen = {
                                viewModel.rememberSearch()
                                viewModel.openArticle(article)
                            })
                        },
                    )
                    Tab.SAVED -> SavedScreen(bookmarks, storyActions = { actionsFor(it) })
                } }
            }
            BottomBar(tab, onSelect = { tabIndex = it.ordinal })
        }

        // Keyed by stack depth so the animation knows whether a screen is opening or closing.
        AnimatedContent(
            targetState = screens.size to screens.lastOrNull(),
            transitionSpec = {
                if (targetState.first > initialState.first) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "overlay screens",
        ) { (_, screen) ->
            if (screen == null) return@AnimatedContent
            Surface(Modifier.fillMaxSize(), color = Newsly.colors.background) {
                when (screen) {
                    OverlayScreen.Settings -> SettingsScreen(appSettings, settings, onBack = viewModel::closeScreen)
                    is OverlayScreen.Detail -> ArticleDetailScreen(
                        article = screen.article,
                        saved = screen.article.url in savedUrls,
                        onBack = viewModel::closeScreen,
                        onReadFull = { viewModel.readFullArticle(screen.article) },
                        onToggleBookmark = { viewModel.toggleBookmark(screen.article) },
                    )
                    is OverlayScreen.Web -> ArticleWebScreen(
                        article = screen.article,
                        saved = screen.article.url in savedUrls,
                        onBack = viewModel::closeScreen,
                        onToggleBookmark = { viewModel.toggleBookmark(screen.article) },
                    )
                }
            }
        }

        // Clear of the bottom bar, or of the story screen's pinned button.
        val snackbarLift = when (screens.lastOrNull()) {
            null -> 84.dp
            is OverlayScreen.Detail -> 84.dp
            else -> 12.dp
        }
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = snackbarLift),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = Newsly.colors.strong,
                contentColor = Newsly.colors.onStrong,
                actionColor = Newsly.colors.accent,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    optionsFor?.let { article ->
        StoryOptionsSheet(
            article = article,
            saved = article.url in savedUrls,
            onToggleBookmark = { viewModel.toggleBookmark(article) },
            onDismiss = { optionsFor = null },
        )
    }

    // The web screen handles back itself so it can step back through pages first.
    BackHandler(enabled = screens.lastOrNull().let { it != null && it !is OverlayScreen.Web }) {
        viewModel.closeScreen()
    }
    BackHandler(enabled = screens.isEmpty() && tab != Tab.HOME) { tabIndex = Tab.HOME.ordinal }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Newsly.colors
    Column(Modifier.fillMaxWidth().background(c.surface.copy(alpha = 0.97f))) {
        HorizontalDivider(color = c.line.copy(alpha = 0.6f))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == current
                Column(
                    Modifier
                        .width(88.dp)
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(width = 60.dp, height = 32.dp).background(if (on) c.strong else c.surface.copy(alpha = 0f), RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (tab == Tab.SAVED && on) BookmarkFilled else tab.icon,
                            contentDescription = null,
                            tint = if (on) c.onStrong else c.muted,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                        color = if (on) c.ink else c.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(appSettings: AppSettings, settings: com.example.newsly.data.Settings, onBack: () -> Unit) {
    val c = Newsly.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(NewslyIcons.Back, "Back", tint = c.ink) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text("Changes apply straight away.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            ThemeChooser(
                settings,
                appSettings::setThemeMode,
                appSettings::setAccent,
                appSettings::setTextSize,
                Modifier.padding(top = 14.dp, bottom = 28.dp),
            )
        }
    }
}
