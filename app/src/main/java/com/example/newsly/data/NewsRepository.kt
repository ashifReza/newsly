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
    /** An empty feed is never fresh: it's worth trying again rather than showing nothing for ten minutes. */
    fun isFresh(now: Instant = Instant.now()): Boolean =
        articles.isNotEmpty() && Duration.between(fetchedAt, now) < NewsRepository.FRESH_FOR
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
        val feed = Feed(publishers.headlines(category).tidy(max = MAX_WORLD_ARTICLES), Instant.now())
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
        val (articles, scope) = placeNews(location, category)
        val feed = Feed(articles, Instant.now(), scope, locationKey(location))
        save(localKey(category), feed)
        return feed
    }

    /** The saved copy of World filtered to one country ([countryCode], e.g. "JP"), if any. */
    suspend fun savedCountry(countryCode: String, category: Category): Feed? = load(countryKey(countryCode, category))

    /**
     * Downloads news from and about one country for the World tab's country filter: its own
     * outlets where the app knows them, plus recent Google News coverage of it.
     */
    suspend fun refreshCountry(countryCode: String, countryName: String, category: Category): Feed {
        val place = UserLocation(city = null, countryCode = countryCode, countryName = countryName, isApproximate = true)
        val (articles, scope) = placeNews(place, category)
        val feed = Feed(articles, Instant.now(), scope)
        save(countryKey(countryCode, category), feed)
        return feed
    }

    private suspend fun placeNews(location: UserLocation, category: Category): Pair<List<Article>, String> {
        val country = "\"${location.countryName}\""
        return if (category.localQuery != null) {
            categoryNews(location, country, category) to location.countryName
        } else {
            localTop(location, country)
        }
    }

    /**
     * A category for one country: Google News search, plus stories from the World outlets'
     * matching feeds that mention the country, which bring photos.
     */
    private suspend fun categoryNews(location: UserLocation, country: String, category: Category): List<Article> = coroutineScope {
        val search = async { runCatching { googleNews.search("$country (${category.localQuery})") } }
        val mentions = async { runCatching { publishers.mentioning(category, countryTerms(location)) } }
        val results = listOf(search.await(), mentions.await())
        if (results.all { it.isFailure }) throw search.await().exceptionOrNull()!!
        val photoNews = mentions.await().getOrDefault(emptyList()).recent().tidy(PER_SOURCE_LOCAL, max = MAX_PHOTO_STORIES)
        val photoTitles = photoNews.mapTo(HashSet()) { titleKey(it.title) }
        val searchNews = search.await().getOrDefault(emptyList()).filter { titleKey(it.title) !in photoTitles }
        val articles = withPhotoStories(photoNews, searchNews, MAX_ARTICLES)
        if (articles.isEmpty()) results.firstOrNull { it.isFailure }?.let { throw it.exceptionOrNull()!! }
        articles.photoFirst()
    }

    private fun countryTerms(location: UserLocation) = Country.searchTerms(location.countryCode, location.countryName)

    private suspend fun localTop(location: UserLocation, country: String): Pair<List<Article>, String> = coroutineScope {
        val city = location.city
        val cityFeeds = if (city == null) emptyList() else listOf(
            async { runCatching { googleNews.place(city) } },
            async { runCatching { googleNews.search("\"$city\"", days = 3) } },
        )
        // The country's own outlets, and World outlets' stories that mention it: both carry photos.
        val outletFeed = async {
            runCatching {
                val mentions = async { runCatching { publishers.mentioning(Category.TOP, countryTerms(location)) } }
                val own = publishers.localHeadlines(location.countryCode)
                own + mentions.await().getOrDefault(emptyList())
            }
        }
        val countryFeed = async { runCatching { googleNews.search(country) } }
        val results = cityFeeds.awaitAll() + outletFeed.await() + countryFeed.await()
        if (results.all { it.isFailure }) throw countryFeed.await().exceptionOrNull()!!

        // The same story often arrives both from the outlet (with a photo) and through Google
        // News (without); keep the outlet's copy.
        val outletNews = outletFeed.await().getOrDefault(emptyList())
        val outletTitles = outletNews.mapTo(HashSet()) { titleKey(it.title) }
        fun List<Article>.notFromOutlets() = filter { titleKey(it.title) !in outletTitles }

        val photoNews = outletNews.recent().tidy(PER_SOURCE_OUTLET, max = MAX_PHOTO_STORIES)
        val cityNews = cityFeeds.awaitAll().flatMap { it.getOrDefault(emptyList()) }.notFromOutlets().tidy(PER_SOURCE_LOCAL)
        val countryNews = countryFeed.await().getOrDefault(emptyList()).notFromOutlets()
        val (articles, scope) = if (city != null && cityNews.size >= MIN_CITY_RESULTS) {
            // The newest city stories lead; the rest mix with the country's by time. Leading with all
            // of them would let a big city's searches (as busy as the country's) push the outlets out.
            val lead = cityNews.take(CITY_LEAD)
            val leadUrls = lead.mapTo(HashSet()) { it.url }
            val rest = withPhotoStories(photoNews, (cityNews.drop(CITY_LEAD) + countryNews).filter { it.url !in leadUrls }, MAX_ARTICLES - lead.size)
            (lead + rest) to city
        } else {
            withPhotoStories(photoNews, countryNews, MAX_ARTICLES) to location.countryName
        }
        // Nothing to show and something failed: report the failure rather than an empty feed
        // (a country without listed outlets "succeeds" with no stories of its own).
        if (articles.isEmpty()) results.firstOrNull { it.isFailure }?.let { throw it.exceptionOrNull()!! }
        articles.photoFirst() to scope
    }

    /**
     * Mixes [photoNews] (already tidied) with [others] by time, keeping every photo story and
     * filling the rest of [max] with the newest of the others. Cutting the mix by time alone
     * dropped most photo stories: Google News returns many more, and newer, stories.
     */
    private fun withPhotoStories(photoNews: List<Article>, others: List<Article>, max: Int): List<Article> {
        val photoUrls = photoNews.mapTo(HashSet()) { it.url }
        val fill = others.filter { it.url !in photoUrls }.tidy(PER_SOURCE_LOCAL, max = (max - photoNews.size).coerceAtLeast(0))
        return (photoNews + fill).sortedByDescending { it.publishedAt }
    }

    /** Stories from the last few days; feeds can reach back a week or more. */
    private fun List<Article>.recent(now: Instant = Instant.now()): List<Article> =
        filter { a -> a.publishedAt?.let { Duration.between(it, now) < PHOTO_STORY_MAX_AGE } ?: false }

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
    private fun countryKey(countryCode: String, category: Category) = "country:${countryCode.uppercase()}:$category"

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
                rank = it.rank,
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
                    rank = a.rank,
                )
            },
        )
    }

    /**
     * Newest first, no duplicates, at most [perSource] stories from any one outlet
     * so a few busy outlets don't fill the feed, and capped so it stays manageable.
     */
    private fun List<Article>.tidy(perSource: Int = Int.MAX_VALUE, max: Int = MAX_ARTICLES): List<Article> {
        val perOutlet = mutableMapOf<String, Int>()
        return distinctBy { it.url }
            .sortedByDescending { it.publishedAt }
            .filter { perOutlet.merge(it.sourceName, 1, Int::plus)!! <= perSource }
            .take(max)
    }

    companion object {
        /** Saved feeds younger than this are shown without downloading again. */
        val FRESH_FOR: Duration = Duration.ofMinutes(10)
        private const val MIN_CITY_RESULTS = 3
        private const val CITY_LEAD = 15
        private const val MAX_ARTICLES = 80
        /** World draws on about thirty outlets; leave room for the quieter ones. */
        private const val MAX_WORLD_ARTICLES = 120
        /** Google News feeds mix dozens of outlets; keep any single one from dominating. */
        private const val PER_SOURCE_LOCAL = 5
        /** The country's own outlets bring photos, so they may contribute a little more. */
        private const val PER_SOURCE_OUTLET = 8
        /** Room kept for photo stories in a place's feed, so Google News's many stories can't crowd them out. */
        private const val MAX_PHOTO_STORIES = 40
        private val PHOTO_STORY_MAX_AGE: Duration = Duration.ofDays(3)
    }
}
