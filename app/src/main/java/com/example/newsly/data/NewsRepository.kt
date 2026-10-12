package com.example.newsly.data

import com.example.newsly.data.cache.FeedArticleEntity
import com.example.newsly.data.cache.FeedCacheDao
import com.example.newsly.data.cache.FeedEntity
import com.example.newsly.data.remote.GoogleNewsRss
import com.example.newsly.data.remote.PublisherFeeds
import com.example.newsly.location.UserLocation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * A feed as last downloaded. [scope] is where local stories are from ("Dhaka",
 * "Bangladesh"); null for World.
 */
data class Feed(
    val articles: List<Article>,
    val fetchedAt: Instant,
    val scope: String? = null,
    val locationKey: String? = null,
) {
    fun isFresh(now: Instant = Instant.now()): Boolean =
        Duration.between(fetchedAt, now) < NewsRepository.FRESH_FOR
}

/**
 * World and local feeds are saved on the device after every download, so the
 * app can show them instantly on launch and keep showing them offline.
 */
class NewsRepository(
    private val publishers: PublisherFeeds,
    private val googleNews: GoogleNewsRss,
    private val cacheDao: FeedCacheDao,
) {
    /** The saved copy of the World feed for [category], if any. */
    suspend fun savedWorld(category: Category): Feed? = load(worldKey(category))

    /** Downloads the World feed for [category] and saves it. */
    suspend fun refreshWorld(category: Category): Feed {
        val feed = Feed(publishers.headlines(category).tidy(), Instant.now())
        save(worldKey(category), feed)
        return feed
    }

    /** The saved copy of the local feed for [category], from wherever the user last was. */
    suspend fun savedLocal(category: Category): Feed? = load(localKey(category))

    /**
     * Downloads local news and saves it.
     * Top: the city's own feed, falling back to recent stories about the country.
     * Other categories: a country-wide search, since city-level searches are too noisy.
     */
    suspend fun refreshLocal(location: UserLocation, category: Category): Feed {
        val country = "\"${location.countryName}\""
        val (articles, scope) = run {
            if (category.localQuery != null) {
                return@run googleNews.search("$country (${category.localQuery})").tidy() to location.countryName
            }
            location.city?.let { city ->
                val cityNews = googleNews.place(city).tidy()
                if (cityNews.size >= MIN_CITY_RESULTS) return@run cityNews to city
            }
            googleNews.search(country).tidy() to location.countryName
        }
        val feed = Feed(articles, Instant.now(), scope, locationKey(location))
        save(localKey(category), feed)
        return feed
    }

    /** Identifies where a local feed was fetched for; a different key means the user has moved. */
    fun locationKey(location: UserLocation): String =
        "${location.countryCode.lowercase()}:${location.city.orEmpty().lowercase()}"

    /** Stories from the past week matching [query]. Searches are kept in memory only. */
    suspend fun search(query: String, forceRefresh: Boolean = false): List<Article> {
        val key = query.lowercase()
        if (!forceRefresh) {
            searchLock.withLock { searchCache[key] }?.let { if (it.isFresh()) return it.articles }
        }
        val articles = googleNews.search(query, days = 7).tidy()
        searchLock.withLock { searchCache[key] = Feed(articles, Instant.now()) }
        return articles
    }

    private val searchCache = mutableMapOf<String, Feed>()
    private val searchLock = Mutex()

    private fun worldKey(category: Category) = "world:$category"
    private fun localKey(category: Category) = "local:$category"

    private suspend fun load(feedKey: String): Feed? {
        val feed = cacheDao.feed(feedKey) ?: return null
        val articles = cacheDao.articles(feedKey).map {
            Article(
                title = it.title,
                description = it.description,
                url = it.url,
                imageUrl = it.imageUrl,
                logoUrl = it.logoUrl,
                sourceName = it.sourceName,
                publishedAt = it.publishedAtMillis?.let(Instant::ofEpochMilli),
            )
        }
        return Feed(articles, Instant.ofEpochMilli(feed.fetchedAtMillis), feed.scope, feed.locationKey)
    }

    private suspend fun save(feedKey: String, feed: Feed) {
        cacheDao.replace(
            FeedEntity(feedKey, feed.fetchedAt.toEpochMilli(), feed.scope, feed.locationKey),
            feed.articles.mapIndexed { i, a ->
                FeedArticleEntity(
                    feedKey = feedKey,
                    position = i,
                    url = a.url,
                    title = a.title,
                    description = a.description,
                    imageUrl = a.imageUrl,
                    logoUrl = a.logoUrl,
                    sourceName = a.sourceName,
                    publishedAtMillis = a.publishedAt?.toEpochMilli(),
                )
            },
        )
    }

    /** Newest first, no duplicates, capped so the feed stays manageable. */
    private fun List<Article>.tidy(): List<Article> =
        distinctBy { it.url }
            .sortedByDescending { it.publishedAt }
            .take(MAX_ARTICLES)

    companion object {
        /** Saved feeds younger than this are shown without downloading again. */
        val FRESH_FOR: Duration = Duration.ofMinutes(10)
        private const val MIN_CITY_RESULTS = 3
        private const val MAX_ARTICLES = 40
    }
}
