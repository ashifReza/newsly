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
            // BBC only lists a 240px thumbnail; the same image is served at larger sizes.
            improveImage = { it.replace("/standard/240/", "/standard/800/") },
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
    )

    /**
     * Fetches every publisher's feed for [category] in parallel. One feed failing
     * doesn't break the tab. Each publisher contributes at most [PER_PUBLISHER]
     * stories, so outlets that post very often don't crowd out the rest.
     */
    suspend fun headlines(category: Category): List<Article> = coroutineScope {
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
        const val PER_PUBLISHER = 12
    }
}
