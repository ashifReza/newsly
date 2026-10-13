package com.example.newsly.data.remote

import android.util.Xml
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One `<item>` from an RSS feed, before it becomes an app [com.example.newsly.data.Article]. */
data class RssItem(
    val title: String,
    val link: String,
    val description: String?,
    val publishedAt: Instant?,
    val imageUrl: String?,
    /** Text and `url` attribute of `<source>`; Google News uses it for the publisher. */
    val sourceName: String?,
    val sourceUrl: String?,
)

/** Downloads and parses RSS 2.0 feeds, including Media RSS images (`media:content`, `media:thumbnail`). */
class RssClient(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun fetch(url: HttpUrl): List<RssItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) Newsly/0.1")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NewsFeedException(response.code)
            val body = response.body ?: throw IOException("Empty response")
            parse(body.byteStream())
        }
    }

    private fun parse(input: InputStream): List<RssItem> {
        val parser = Xml.newPullParser().apply { setInput(input, null) }
        val items = mutableListOf<RssItem>()
        var item: ItemBuilder? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "item") {
                        item = ItemBuilder()
                        continue
                    }
                    val current = item ?: continue
                    // Android's parser is namespace-aware: <media:content> arrives as "content" in the
                    // Media RSS namespace, and tags like <atom:link> must not be mistaken for <link>.
                    when (parser.namespace) {
                        "" -> when (parser.name) {
                            "title" -> current.title = parser.nextText()
                            "link" -> current.link = parser.nextText()
                            "description" -> current.description = parser.nextText()
                            "pubDate" -> current.pubDate = parser.nextText()
                            "source" -> {
                                current.sourceUrl = parser.getAttributeValue(null, "url")
                                current.sourceName = parser.nextText()
                            }
                            "enclosure" -> current.offerImage(
                                url = parser.getAttributeValue(null, "url"),
                                width = null,
                                isImage = parser.getAttributeValue(null, "type")?.startsWith("image/") == true,
                            )
                        }
                        MEDIA_RSS_NS -> if (parser.name == "content" || parser.name == "thumbnail") {
                            current.offerImage(
                                url = parser.getAttributeValue(null, "url"),
                                width = parser.getAttributeValue(null, "width")?.toIntOrNull(),
                                isImage = parser.getAttributeValue(null, "medium")?.let { it == "image" } ?: true,
                            )
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    item?.build()?.let { items += it }
                    item = null
                }
            }
        }
        return items
    }

    private companion object {
        val DATE_FORMATS = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, d MMM yy HH:mm:ss Z", java.util.Locale.ENGLISH),
        )
        const val MEDIA_RSS_NS = "http://search.yahoo.com/mrss/"
    }

    private class ItemBuilder {
        var title: String? = null
        var link: String? = null
        var description: String? = null
        var pubDate: String? = null
        var sourceName: String? = null
        var sourceUrl: String? = null
        private var imageUrl: String? = null
        private var imageWidth = -1

        /** Feeds often list several sizes of the same photo; keep the widest. */
        fun offerImage(url: String?, width: Int?, isImage: Boolean) {
            if (url.isNullOrBlank() || !isImage) return
            val w = width ?: 0
            if (w > imageWidth) {
                imageUrl = url
                imageWidth = w
            }
        }

        fun build(): RssItem? {
            val t = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val l = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return RssItem(
                title = t,
                link = l,
                description = description?.let(::plainText)?.takeIf { it.isNotEmpty() },
                publishedAt = pubDate?.let {
                    // Some feeds (The Daily Star) write a two-digit year: "Tue, 13 Oct 26 00:00:00 +0600".
                    DATE_FORMATS.firstNotNullOfOrNull { format ->
                        runCatching { ZonedDateTime.parse(it.trim(), format).toInstant() }.getOrNull()
                    }
                },
                imageUrl = imageUrl,
                sourceName = sourceName?.trim(),
                sourceUrl = sourceUrl,
            )
        }

        private fun plainText(html: String): String =
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                .replace("Continue reading...", "")
                .replace(Regex("[ \\t\\u00A0]+"), " ")
                .replace(Regex(" *\\n *"), "\n")
                .replace(Regex("\\n+"), "\n\n") // A blank line between paragraphs.
                .trim()
    }
}

/** A publisher logo for [siteUrl] from Google's favicon service. */
fun logoUrlFor(siteUrl: String?): String? {
    val host = siteUrl?.toHttpUrlOrNull()?.host ?: return null
    return "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON" +
        "&fallback_opts=TYPE,SIZE,URL&url=https://$host&size=128"
}

class NewsFeedException(val code: Int) : Exception(
    if (code == 429 || code == 503) "The news server is rate-limiting requests. Try again shortly."
    else "News feed error ($code)."
)
