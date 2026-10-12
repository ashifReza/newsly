package com.example.newsly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.newsly.NewsApplication
import com.example.newsly.data.Article
import com.example.newsly.data.Category
import com.example.newsly.data.Feed
import com.example.newsly.data.NewsRepository
import com.example.newsly.data.RecentSearches
import com.example.newsly.data.bookmarks.BookmarkEntity
import com.example.newsly.data.bookmarks.BookmarkRepository
import com.example.newsly.location.LocationProvider
import com.example.newsly.location.UserLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

sealed interface FeedState {
    data object Loading : FeedState

    data class Success(
        val articles: List<Article>,
        /** When these stories were downloaded; null for results that are never saved (search). */
        val fetchedAt: Instant? = null,
        /** Set when a refresh failed and these are the saved stories from [fetchedAt]. */
        val refreshProblem: RefreshProblem? = null,
    ) : FeedState

    data class Error(val message: String) : FeedState
}

enum class RefreshProblem { OFFLINE, FAILED }

/** Screens opened on top of the tabs. The tabs stay underneath, keeping their scroll positions. */
sealed interface OverlayScreen {
    /** Appearance: theme, accent colour, text size. */
    data object Settings : OverlayScreen

    /** Photo, headline and summary from the feed. */
    data class Detail(val article: Article) : OverlayScreen

    /** The publisher's own page, shown inside the app. */
    data class Web(val article: Article) : OverlayScreen
}

/** One-off messages shown as a snackbar. */
sealed interface BookmarkEvent {
    data object Saved : BookmarkEvent
    data class Removed(val bookmark: BookmarkEntity) : BookmarkEvent
}

data class SearchUiState(
    /** What's in the search box. */
    val query: String = "",
    /** The query the results are for; null before the first search. */
    val searchedQuery: String? = null,
    val results: FeedState? = null,
    /** True while a search is in flight, even if older results are still showing. */
    val searching: Boolean = false,
)

data class NewsUiState(
    val category: Category = Category.TOP,
    val world: FeedState = FeedState.Loading,
    val local: FeedState = FeedState.Loading,
    /** Where the local feed is from, e.g. "Dhaka" or "Bangladesh". */
    val localScope: String? = null,
    val location: UserLocation? = null,
    val worldRefreshing: Boolean = false,
    val localRefreshing: Boolean = false,
)

class NewsViewModel(
    private val repository: NewsRepository,
    private val locationProvider: LocationProvider,
    private val bookmarkRepository: BookmarkRepository,
    private val recentSearches: RecentSearches,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _screens = MutableStateFlow<List<OverlayScreen>>(emptyList())
    val screens: StateFlow<List<OverlayScreen>> = _screens.asStateFlow()

    val hasLocationPermission: Boolean get() = locationProvider.hasPermission()

    /** Saved stories, newest save first. Null until the database has loaded. */
    val bookmarks: StateFlow<List<Article>?> = bookmarkRepository.bookmarks
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** URLs of saved stories, for filling in bookmark icons. */
    val savedUrls: StateFlow<Set<String>> = bookmarks
        .map { list -> list.orEmpty().mapTo(HashSet()) { it.url } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()
    val recentSearchList: StateFlow<List<String>> = recentSearches.items
    private var searchJob: Job? = null
    private var typingJob: Job? = null

    private val _bookmarkEvents = Channel<BookmarkEvent>(Channel.BUFFERED)
    val bookmarkEvents: Flow<BookmarkEvent> = _bookmarkEvents.receiveAsFlow()

    // Tracked so switching category quickly can't let an older, slower request win.
    private var worldJob: Job? = null
    private var localJob: Job? = null

    init {
        loadWorld()
        loadLocal(resolveLocation = true)
    }

    /** Stories without a photo or summary have nothing to preview, so they go straight to the page. */
    fun openArticle(article: Article) {
        val screen = if (article.imageUrl == null && article.description == null) {
            OverlayScreen.Web(article)
        } else {
            OverlayScreen.Detail(article)
        }
        _screens.update { it + screen }
    }

    fun readFullArticle(article: Article) = _screens.update { it + OverlayScreen.Web(article) }

    /** Searches as the user types, once they pause and have typed enough to be meaningful. */
    fun onSearchQueryChange(query: String) {
        _searchState.update { it.copy(query = query) }
        typingJob?.cancel()
        val q = normalize(query)
        when {
            q.length < MIN_LIVE_SEARCH_LENGTH -> clearResults()
            q != _searchState.value.searchedQuery ->
                typingJob = viewModelScope.launch {
                    delay(TYPING_DEBOUNCE_MS)
                    runSearch(q, forceRefresh = false)
                }
        }
    }

    /** Search key pressed or a recent search tapped: search now and remember the query. */
    fun search(query: String = _searchState.value.query, forceRefresh: Boolean = false) {
        typingJob?.cancel()
        val q = normalize(query)
        if (q.isEmpty()) return
        recentSearches.add(q)
        _searchState.update { it.copy(query = q) }
        // Typing may already have fetched these results; don't fetch them twice.
        if (q != _searchState.value.searchedQuery || forceRefresh || _searchState.value.results is FeedState.Error) {
            runSearch(q, forceRefresh)
        }
    }

    /** Remembers the current query once the user opens one of its results. */
    fun rememberSearch() {
        _searchState.value.searchedQuery?.let(recentSearches::add)
    }

    private fun runSearch(q: String, forceRefresh: Boolean) {
        // Keep showing the previous results while the new ones load.
        _searchState.update {
            it.copy(
                searchedQuery = q,
                results = it.results as? FeedState.Success ?: FeedState.Loading,
                searching = true,
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val result = runCatching { repository.search(q, forceRefresh) }
            ensureActive()
            _searchState.update {
                it.copy(
                    results = result.fold({ a -> FeedState.Success(a) }, { e -> FeedState.Error(e.userMessage()) }),
                    searching = false,
                )
            }
        }
    }

    private fun clearResults() {
        searchJob?.cancel()
        _searchState.update { it.copy(searchedQuery = null, results = null, searching = false) }
    }

    private fun normalize(query: String) = query.trim().replace(Regex("\\s+"), " ")

    /** Clears the box and results, back to the recent-searches list. */
    fun clearSearch() {
        typingJob?.cancel()
        searchJob?.cancel()
        _searchState.value = SearchUiState()
    }

    fun removeRecentSearch(query: String) = recentSearches.remove(query)

    fun clearRecentSearches() = recentSearches.clear()

    fun openSettings() = _screens.update { it + OverlayScreen.Settings }

    fun closeScreen() = _screens.update { it.dropLast(1) }

    fun toggleBookmark(article: Article) {
        viewModelScope.launch {
            if (article.url in savedUrls.value) {
                bookmarkRepository.remove(article.url)?.let { _bookmarkEvents.send(BookmarkEvent.Removed(it)) }
            } else {
                bookmarkRepository.save(article)
                _bookmarkEvents.send(BookmarkEvent.Saved)
            }
        }
    }

    /** Puts a removed bookmark back, keeping its original place in the list. */
    fun undoRemove(bookmark: BookmarkEntity) {
        viewModelScope.launch { bookmarkRepository.restore(bookmark) }
    }

    fun selectCategory(category: Category) {
        if (category == _uiState.value.category) return
        _uiState.update { it.copy(category = category, world = FeedState.Loading, local = FeedState.Loading) }
        loadWorld()
        loadLocal(resolveLocation = false)
    }

    fun refreshWorld() = loadWorld(forceRefresh = true)

    fun refreshLocal() = loadLocal(resolveLocation = false, forceRefresh = true)

    /** Call after the permission dialog closes, so a newly granted permission is used. */
    fun onLocationPermissionResult() = loadLocal(resolveLocation = true)

    /**
     * Shows the saved World feed straight away, then downloads a new one unless
     * the saved copy is still fresh. If the download fails, the saved stories stay.
     */
    private fun loadWorld(forceRefresh: Boolean = false) {
        worldJob?.cancel()
        worldJob = viewModelScope.launch {
            val category = _uiState.value.category
            _uiState.update { it.copy(worldRefreshing = forceRefresh) }

            val saved = runCatching { repository.savedWorld(category) }.getOrNull()
            ensureActive() // runCatching swallows cancellation; stop here if a newer load replaced this one.
            if (saved != null) {
                _uiState.update { it.copy(world = FeedState.Success(saved.articles, saved.fetchedAt)) }
                if (!forceRefresh && saved.isFresh()) {
                    _uiState.update { it.copy(worldRefreshing = false) }
                    return@launch
                }
            }

            val result = runCatching { repository.refreshWorld(category) }
            ensureActive()
            _uiState.update { it.copy(world = result.toFeedState(saved), worldRefreshing = false) }
        }
    }

    /**
     * Shows the saved local feed straight away (finding the user's location can
     * take a few seconds), then downloads a new one if it's stale or the user has moved.
     */
    private fun loadLocal(resolveLocation: Boolean, forceRefresh: Boolean = false) {
        localJob?.cancel()
        localJob = viewModelScope.launch {
            val category = _uiState.value.category
            _uiState.update { it.copy(localRefreshing = forceRefresh) }

            val saved = runCatching { repository.savedLocal(category) }.getOrNull()
            ensureActive()
            if (saved != null) {
                _uiState.update {
                    it.copy(local = FeedState.Success(saved.articles, saved.fetchedAt), localScope = saved.scope)
                }
            }

            val location = _uiState.value.location.takeUnless { resolveLocation }
                ?: locationProvider.currentLocation()
            _uiState.update { it.copy(location = location) }

            val sameLocation = saved?.locationKey == repository.locationKey(location)
            if (!forceRefresh && saved != null && sameLocation && saved.isFresh()) {
                _uiState.update { it.copy(localRefreshing = false) }
                return@launch
            }

            val result = runCatching { repository.refreshLocal(location, category) }
            ensureActive()
            _uiState.update {
                it.copy(
                    local = result.toFeedState(saved),
                    localScope = result.getOrNull()?.scope ?: saved?.scope ?: location.city ?: location.countryName,
                    localRefreshing = false,
                )
            }
        }
    }

    /** A fresh download, or the saved copy with a note that it couldn't be refreshed. */
    private fun Result<Feed>.toFeedState(saved: Feed?): FeedState = fold(
        onSuccess = { FeedState.Success(it.articles, it.fetchedAt) },
        onFailure = { e ->
            if (saved == null) {
                FeedState.Error(e.userMessage())
            } else {
                val problem = if (e is IOException) RefreshProblem.OFFLINE else RefreshProblem.FAILED
                FeedState.Success(saved.articles, saved.fetchedAt, problem)
            }
        },
    )

    private fun Throwable.userMessage(): String = when (this) {
        is java.io.IOException -> "No internet connection."
        else -> message ?: "Something went wrong."
    }

    companion object {
        private const val MIN_LIVE_SEARCH_LENGTH = 3
        private const val TYPING_DEBOUNCE_MS = 500L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as NewsApplication).container
                NewsViewModel(
                    container.newsRepository,
                    container.locationProvider,
                    container.bookmarkRepository,
                    container.recentSearches,
                )
            }
        }
    }
}
