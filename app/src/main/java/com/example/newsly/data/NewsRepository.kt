package com.example.newsly.data

import com.example.newsly.data.cache.FeedArticleEntity
import com.example.newsly.data.cache.FeedCacheDao
import com.example.newsly.data.cache.FeedEntity
import com.example.newsly.data.remote.GoogleNewsRss
import com.example.newsly.data.remote.PublisherFeeds
import com.example.newsly.location.UserLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
     * Top: stories about the city first (its Google News feed plus a search for it), then
     * the country's own outlets, which carry photos, mixed with recent stories from across
     * the country. A city feed alone is often just a dozen stories from two or three outlets.
     * Other categories: a country-wide search, since city-level searches are too noisy.
     */
    suspend fun refreshLocal(location: UserLocation, category: Category): Feed {
        val country = "\"${location.countryName}\""
        val (articles, scope) = if (category.localQuery != null) {
            googleNews.search("$country (${category.localQuery})").tidy(PER_SOURCE_LOCAL) to location.countryName
        } else {
            localTop(location, country)
        }
        val feed = Feed(articles, Instant.now(), scope, locationKey(location))
        save(localKey(category), feed)
        return feed
    }

    private suspend fun localTop(location: UserLocation, country: String): Pair<List<Article>, String> = coroutineScope {
        val city = location.city
        val cityFeeds = if (city == null) emptyList() else listOf(
            async { runCatching { googleNews.place(city) } },
            async { runCatching { googleNews.search("\"$city\"", days = 3) } },
        )
        val outletFeed = async { runCatching { publishers.localHeadlines(location.countryCode) } }
        val countryFeed = async { runCatching { googleNews.search(country) } }
        val results = cityFeeds.awaitAll() + outletFeed.await() + countryFeed.await()
        if (results.all { it.isFailure }) throw countryFeed.await().exceptionOrNull()!!

        // The same story often arrives both from the outlet (with a photo) and through Google
        // News (without); keep the outlet's copy.
        val outletNews = outletFeed.await().getOrDefault(emptyList())
        val outletTitles = outletNews.mapTo(HashSet()) { titleKey(it.title) }
        fun List<Article>.notFromOutlets() = filter { titleKey(it.title) !in outletTitles }

        val cityNews = cityFeeds.awaitAll().flatMap { it.getOrDefault(emptyList()) }.notFromOutlets().tidy(PER_SOURCE_LOCAL)
        val countryNews = (outletNews + countryFeed.await().getOrDefault(emptyList()).notFromOutlets()).tidy(PER_SOURCE_OUTLET)
        val (articles, scope) = if (city != null && cityNews.size >= MIN_CITY_RESULTS) {
            // The newest city stories lead; the rest mix with the country's by time. Leading with all
            // of them would let a big city's searches (as busy as the country's) push the outlets out.
            val lead = cityNews.take(CITY_LEAD)
            val leadUrls = lead.mapTo(HashSet()) { it.url }
            val rest = (cityNews.drop(CITY_LEAD) + countryNews).filter { it.url !in leadUrls }.tidy(PER_SOURCE_OUTLET)
            (lead + rest).take(MAX_ARTICLES) to city
        } else {
            countryNews to location.countryName
        }
        articles.photoFirst() to scope
    }

    /** Leads with the newest story that has a photo, so the feed opens on a picture when it can. */
    private fun List<Article>.photoFirst(): List<Article> {
        val lead = firstOrNull { it.imageUrl != null } ?: return this
        return listOf(lead) + minus(lead)
    }

    /** Headline reduced to letters and digits, for spotting the same story from two sources. */
    private fun titleKey(title: String) = title.lowercase().filter { it.isLetterOrDigit() }.take(60)

    /** Identifies where a local feed was fetched for; a different key means the user has moved. */
    fun locationKey(location: UserLocation): String =
        "${location.countryCode.lowercase()}:${location.city.orEmpty().lowercase()}"

    /** Stories from the past week matching [query]. Searches are kept in memory only. */
    suspend fun search(query: String, forceRefresh: Boolean = false): List<Article> {
        val key = query.lowercase()
        if (!forceRefresh) {
            searchLock.withLock { searchCache[key] }?.let { if (it.isFresh()) return it.articles }
        }
        val articles = googleNews.search(query, days = 7).tidy(PER_SOURCE_LOCAL)
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

    /**
     * Newest first, no duplicates, at most [perSource] stories from any one outlet
     * so a few busy outlets don't fill the feed, and capped so it stays manageable.
     */
    private fun List<Article>.tidy(perSource: Int = Int.MAX_VALUE): List<Article> {
        val perOutlet = mutableMapOf<String, Int>()
        return distinctBy { it.url }
            .sortedByDescending { it.publishedAt }
            .filter { perOutlet.merge(it.sourceName, 1, Int::plus)!! <= perSource }
            .take(MAX_ARTICLES)
    }

    companion object {
        /** Saved feeds younger than this are shown without downloading again. */
        val FRESH_FOR: Duration = Duration.ofMinutes(10)
        private const val MIN_CITY_RESULTS = 3
        private const val CITY_LEAD = 15
        private const val MAX_ARTICLES = 80
        /** Google News feeds mix dozens of outlets; keep any single one from dominating. */
        private const val PER_SOURCE_LOCAL = 5
        /** The country's own outlets bring photos, so they may contribute a little more. */
        private const val PER_SOURCE_OUTLET = 8
    }
}
