package com.example.newsly

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.example.newsly.data.AppSettings
import com.example.newsly.data.NewsRepository
import com.example.newsly.data.RecentSearches
import com.example.newsly.data.bookmarks.BookmarkDatabase
import com.example.newsly.data.bookmarks.BookmarkRepository
import com.example.newsly.data.cache.FeedCacheDatabase
import com.example.newsly.data.remote.GoogleNewsRss
import com.example.newsly.data.remote.PublisherFeeds
import com.example.newsly.data.remote.RssClient
import com.example.newsly.data.remote.StoryImageFinder
import com.example.newsly.location.LocationProvider
import okhttp3.OkHttpClient

class NewsApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DebugLocationReceiver.registerIfDebuggable(this, container.locationProvider)
    }

    /**
     * Keeps story photos and logos on disk, and serves them from there even when
     * the publisher's cache headers say they're stale, so saved feeds have
     * pictures offline.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}

/** Manual dependency container. Swap for Hilt once the app grows. */
class AppContainer(app: Application) {
    private val httpClient = OkHttpClient()
    private val rssClient = RssClient(httpClient)
    private val feedCache = FeedCacheDatabase.create(app)
    val newsRepository = NewsRepository(
        PublisherFeeds(rssClient),
        GoogleNewsRss(rssClient),
        feedCache.feedCacheDao(),
    )
    val storyImageFinder = StoryImageFinder(httpClient, feedCache.storyImageDao())
    val locationProvider = LocationProvider(app)
    val bookmarkRepository = BookmarkRepository(BookmarkDatabase.create(app).bookmarkDao())
    val recentSearches = RecentSearches(app)
    val appSettings = AppSettings(app)
}
