package com.example.newsly.data.remote

import com.example.newsly.data.Article
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Google News RSS feeds, used for local news. No API key needed, but the feed
 * format is unofficial and may change, and its terms for use in a published app
 * are unclear. Fine for development and personal use.
 *
 * These feeds carry no story photos, so cards show the publisher's logo instead.
 */
class GoogleNewsRss(private val rss: RssClient) {

    /** Headlines Google ties to a place. Works for cities; often empty for whole countries. */
    suspend fun place(name: String): List<Article> = fetch(feedUrl("headlines/section/geo/$name"))

    /** Recent stories matching [query], limited to the last [days] days. */
    suspend fun search(query: String, days: Int = 2): List<Article> =
        fetch(feedUrl("search") { addQueryParameter("q", "$query when:${days}d") })

    private fun feedUrl(path: String, extra: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        "https://news.google.com/rss".toHttpUrl().newBuilder()
            .addPathSegments(path)
            .addQueryParameter("hl", "en-US")
            .addQueryParameter("gl", "US")
            .addQueryParameter("ceid", "US:en")
            .apply(extra)
            .build()

    private suspend fun fetch(url: HttpUrl): List<Article> =
        rss.fetch(url).map { item ->
            Article(
                // Google appends " - Source" to every headline.
                title = item.sourceName?.let { item.title.removeSuffix(" - $it") } ?: item.title,
                // Google's description only repeats the headline as a link.
                description = null,
                url = item.link,
                imageUrl = null,
                logoUrl = logoUrlFor(item.sourceUrl),
                sourceName = item.sourceName ?: "Google News",
                publishedAt = item.publishedAt,
            )
        }
}
