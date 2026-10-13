package com.example.newsly.data.remote

import com.example.newsly.data.Article
import com.example.newsly.data.Category
import com.example.newsly.data.Category.BUSINESS
import com.example.newsly.data.Category.ENTERTAINMENT
import com.example.newsly.data.Category.HEALTH
import com.example.newsly.data.Category.SCIENCE
import com.example.newsly.data.Category.SPORTS
import com.example.newsly.data.Category.TECHNOLOGY
import com.example.newsly.data.Category.TOP
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * World news straight from publishers' own RSS feeds, which include story photos.
 * Each publisher sets its own terms for feed use (some allow personal use only),
 * so check them before publishing the app.
 */
class PublisherFeeds(private val rss: RssClient) {

    // BBC only lists a 240px thumbnail; the same image is served at larger sizes.
    private val bbcImage: (String) -> String = { it.replace("/standard/240/", "/standard/800/") }
    // ABC lists sizes up to 1600px; 992px is plenty for a phone and much lighter.
    private val abcImage: (String) -> String = { it.replace("_16x9_1600.", "_16x9_992.") }

    private class Publisher(
        val name: String,
        val site: String,
        /** Feed URL per category; a publisher without a feed for a category is skipped. */
        val feeds: Map<Category, String>,
        val improveImage: (String) -> String = { it },
    )

    private val publishers = listOf(
        Publisher(
            name = "BBC News",
            site = "https://www.bbc.com",
            feeds = mapOf(
                TOP to "https://feeds.bbci.co.uk/news/world/rss.xml",
                BUSINESS to "https://feeds.bbci.co.uk/news/business/rss.xml",
                TECHNOLOGY to "https://feeds.bbci.co.uk/news/technology/rss.xml",
                SCIENCE to "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
                HEALTH to "https://feeds.bbci.co.uk/news/health/rss.xml",
                SPORTS to "https://feeds.bbci.co.uk/sport/rss.xml",
                ENTERTAINMENT to "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
            ),
            improveImage = bbcImage,
        ),
        Publisher(
            name = "The Guardian",
            site = "https://www.theguardian.com",
            feeds = mapOf(
                TOP to "https://www.theguardian.com/world/rss",
                BUSINESS to "https://www.theguardian.com/business/rss",
                TECHNOLOGY to "https://www.theguardian.com/technology/rss",
                SCIENCE to "https://www.theguardian.com/science/rss",
                HEALTH to "https://www.theguardian.com/society/health/rss",
                SPORTS to "https://www.theguardian.com/sport/rss",
                ENTERTAINMENT to "https://www.theguardian.com/culture/rss",
            ),
        ),
        Publisher(
            name = "The New York Times",
            site = "https://www.nytimes.com",
            feeds = mapOf(
                TOP to "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
                BUSINESS to "https://rss.nytimes.com/services/xml/rss/nyt/Business.xml",
                TECHNOLOGY to "https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml",
                SCIENCE to "https://rss.nytimes.com/services/xml/rss/nyt/Science.xml",
                HEALTH to "https://rss.nytimes.com/services/xml/rss/nyt/Health.xml",
                ENTERTAINMENT to "https://rss.nytimes.com/services/xml/rss/nyt/Arts.xml",
            ),
        ),
        Publisher(
            name = "France 24",
            site = "https://www.france24.com",
            feeds = mapOf(
                TOP to "https://www.france24.com/en/rss",
                BUSINESS to "https://www.france24.com/en/business/rss",
                HEALTH to "https://www.france24.com/en/health/rss",
                SPORTS to "https://www.france24.com/en/sport/rss",
                ENTERTAINMENT to "https://www.france24.com/en/culture/rss",
            ),
        ),
        Publisher(
            name = "Sky News",
            site = "https://news.sky.com",
            feeds = mapOf(
                TOP to "https://feeds.skynews.com/feeds/rss/world.xml",
                BUSINESS to "https://feeds.skynews.com/feeds/rss/business.xml",
                TECHNOLOGY to "https://feeds.skynews.com/feeds/rss/technology.xml",
                ENTERTAINMENT to "https://feeds.skynews.com/feeds/rss/entertainment.xml",
            ),
        ),
        Publisher(
            name = "ABC News",
            site = "https://abcnews.go.com",
            feeds = mapOf(
                TOP to "https://abcnews.go.com/abcnews/internationalheadlines",
                BUSINESS to "https://abcnews.go.com/abcnews/moneyheadlines",
                TECHNOLOGY to "https://abcnews.go.com/abcnews/technologyheadlines",
                HEALTH to "https://abcnews.go.com/abcnews/healthheadlines",
                SPORTS to "https://abcnews.go.com/abcnews/sportsheadlines",
                ENTERTAINMENT to "https://abcnews.go.com/abcnews/entertainmentheadlines",
            ),
            improveImage = abcImage,
        ),
        Publisher(
            // No photos in this feed, so its stories show as text cards.
            name = "Al Jazeera",
            site = "https://www.aljazeera.com",
            feeds = mapOf(TOP to "https://www.aljazeera.com/xml/rss/all.xml"),
        ),
        Publisher(
            name = "Ars Technica",
            site = "https://arstechnica.com",
            feeds = mapOf(
                TECHNOLOGY to "https://feeds.arstechnica.com/arstechnica/index",
                SCIENCE to "https://feeds.arstechnica.com/arstechnica/science",
            ),
        ),
        Publisher(
            name = "Wired",
            site = "https://www.wired.com",
            feeds = mapOf(
                // Wired's Business section covers the tech industry.
                TECHNOLOGY to "https://www.wired.com/feed/category/business/latest/rss",
                SCIENCE to "https://www.wired.com/feed/category/science/latest/rss",
            ),
        ),
        Publisher(
            name = "Sky Sports",
            site = "https://www.skysports.com",
            feeds = mapOf(SPORTS to "https://www.skysports.com/rss/12040"),
        ),
    )

    /**
     * Each country's own outlets, used to lead Near you with photo stories. Google News
     * covers everywhere but has no photos; countries missing here just show its stories.
     * Names match how Google News names the outlet, so the two can be balanced.
     */
    private val localPublishers: Map<String, List<Publisher>> = mapOf(
        "BD" to listOf(
            local("The Business Standard", "https://www.tbsnews.net", "https://www.tbsnews.net/top-news/rss.xml"),
            local("Prothom Alo English", "https://en.prothomalo.com", "https://en.prothomalo.com/feed/"),
            // The Daily Star's feed dates every story at midnight, so it would always sort as old;
            // its stories still arrive through Google News.
        ),
        "GB" to listOf(
            local("The Guardian", "https://www.theguardian.com", "https://www.theguardian.com/uk-news/rss"),
            local("BBC", "https://www.bbc.com", "https://feeds.bbci.co.uk/news/england/rss.xml", bbcImage),
            local("Sky News", "https://news.sky.com", "https://feeds.skynews.com/feeds/rss/uk.xml"),
        ),
        "US" to listOf(
            local("ABC News", "https://abcnews.go.com", "https://abcnews.go.com/abcnews/usheadlines", abcImage),
            local("The New York Times", "https://www.nytimes.com", "https://rss.nytimes.com/services/xml/rss/nyt/US.xml"),
        ),
        "IN" to listOf(
            local("The Hindu", "https://www.thehindu.com", "https://www.thehindu.com/news/national/feeder/default.rss"),
            local("The Indian Express", "https://indianexpress.com", "https://indianexpress.com/section/india/feed/"),
            local("The Times of India", "https://timesofindia.indiatimes.com", "https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms"),
            local("Hindustan Times", "https://www.hindustantimes.com", "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml"),
        ),
        "PK" to listOf(local("Dawn", "https://www.dawn.com", "https://www.dawn.com/feeds/home")),
        "AU" to listOf(local("The Guardian", "https://www.theguardian.com", "https://www.theguardian.com/australia-news/rss")),
        "IE" to listOf(local("RTÉ", "https://www.rte.ie", "https://www.rte.ie/feeds/rss/?index=/news/")),
        "NG" to listOf(local("Punch Newspapers", "https://punchng.com", "https://punchng.com/feed/")),
    )

    private fun local(name: String, site: String, feed: String, improveImage: (String) -> String = { it }) =
        Publisher(name, site, mapOf(TOP to feed), improveImage)

    /** Top stories from the country's own outlets; empty for countries without any listed. */
    suspend fun localHeadlines(countryCode: String): List<Article> =
        fetchAll(localPublishers[countryCode.uppercase()].orEmpty(), TOP)

    /**
     * Fetches every publisher's feed for [category] in parallel. One feed failing
     * doesn't break the tab. Each publisher contributes at most [PER_PUBLISHER]
     * stories, so outlets that post very often don't crowd out the rest.
     */
    suspend fun headlines(category: Category): List<Article> = fetchAll(publishers, category)

    private suspend fun fetchAll(publishers: List<Publisher>, category: Category): List<Article> = coroutineScope {
        if (publishers.isEmpty()) return@coroutineScope emptyList()
        val results = publishers.mapNotNull { publisher ->
            val url = publisher.feeds[category] ?: return@mapNotNull null
            async { runCatching { fetch(publisher, url) } }
        }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { result ->
            result.getOrDefault(emptyList()).sortedByDescending { it.publishedAt }.take(PER_PUBLISHER)
        }
    }

    private suspend fun fetch(publisher: Publisher, url: String): List<Article> {
        val logo = logoUrlFor(publisher.site)
        return rss.fetch(url.toHttpUrl()).map { item ->
            Article(
                title = item.title,
                description = item.description,
                url = item.link,
                imageUrl = item.imageUrl?.let(publisher.improveImage),
                logoUrl = logo,
                // Some feeds use <source> for photo credits, so always use the publisher's own name.
                sourceName = publisher.name,
                publishedAt = item.publishedAt,
            )
        }
    }

    private companion object {
        const val PER_PUBLISHER = 10
    }
}
