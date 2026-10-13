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
        // Outlets from around the world, so World isn't only seen from London, New York and Paris.
        world("South China Morning Post", "https://www.scmp.com", "https://www.scmp.com/rss/91/feed"),
        world("The Japan Times", "https://www.japantimes.co.jp", "https://www.japantimes.co.jp/feed/"),
        world("CNA", "https://www.channelnewsasia.com", "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml&category=6311"),
        world("Rappler", "https://www.rappler.com", "https://www.rappler.com/feed/"),
        world("The Hindu", "https://www.thehindu.com", "https://www.thehindu.com/news/international/feeder/default.rss"),
        world("Arab News", "https://www.arabnews.com", "https://www.arabnews.com/rss.xml"),
        world("Africanews", "https://www.africanews.com", "https://www.africanews.com/feed/rss"),
        world("Nation", "https://nation.africa", "https://nation.africa/kenya/rss.xml"),
        world("Buenos Aires Times", "https://www.batimes.com.ar", "https://www.batimes.com.ar/feed"),
        world("MercoPress", "https://en.mercopress.com", "https://en.mercopress.com/rss"),
        world("Euronews", "https://www.euronews.com", "https://www.euronews.com/rss?format=mrss&level=theme&name=news"),
        world("Global News", "https://globalnews.ca", "https://globalnews.ca/world/feed/"),
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
        "JP" to listOf(local("The Japan Times", "https://www.japantimes.co.jp", "https://www.japantimes.co.jp/feed/")),
        "SG" to listOf(local("CNA", "https://www.channelnewsasia.com", "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml&category=10416")),
        "CN" to listOf(local("South China Morning Post", "https://www.scmp.com", "https://www.scmp.com/rss/4/feed")),
        "HK" to listOf(local("South China Morning Post", "https://www.scmp.com", "https://www.scmp.com/rss/2/feed")),
        "KR" to listOf(local("The Korea Times", "https://www.koreatimes.co.kr", "https://www.koreatimes.co.kr/www/rss/nation.xml")),
        "MY" to listOf(local("Malay Mail", "https://www.malaymail.com", "https://www.malaymail.com/feed/rss/malaysia")),
        "PH" to listOf(local("Rappler", "https://www.rappler.com", "https://www.rappler.com/feed/")),
        "AE" to listOf(local("The National", "https://www.thenationalnews.com", "https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml")),
        "SA" to listOf(local("Arab News", "https://www.arabnews.com", "https://www.arabnews.com/rss.xml")),
        "TR" to listOf(local("Daily Sabah", "https://www.dailysabah.com", "https://www.dailysabah.com/rss/turkey")),
        // No photos in these feeds; their links go straight to the article, so the app finds them.
        "IL" to listOf(local("The Times of Israel", "https://www.timesofisrael.com", "https://www.timesofisrael.com/feed/")),
        "RU" to listOf(local("The Moscow Times", "https://www.themoscowtimes.com", "https://www.themoscowtimes.com/rss/news")),
        "MX" to listOf(local("Mexico News Daily", "https://mexiconewsdaily.com", "https://mexiconewsdaily.com/feed/")),
        "FR" to listOf(local("France 24", "https://www.france24.com", "https://www.france24.com/en/france/rss")),
        "DE" to listOf(local("The Local Germany", "https://www.thelocal.de", "https://www.thelocal.de/feeds/rss.php")),
        "IT" to listOf(local("ANSA", "https://www.ansa.it", "https://www.ansa.it/english/news/english_nr_rss.xml")),
        "CA" to listOf(local("Global News", "https://globalnews.ca", "https://globalnews.ca/canada/feed/")),
        "AR" to listOf(local("Buenos Aires Times", "https://www.batimes.com.ar", "https://www.batimes.com.ar/feed")),
        "NZ" to listOf(local("NZ Herald", "https://www.nzherald.co.nz", "https://www.nzherald.co.nz/arc/outboundfeeds/rss/section/nz/?outputType=xml")),
        "ZA" to listOf(local("Daily Maverick", "https://www.dailymaverick.co.za", "https://www.dailymaverick.co.za/dmrss/")),
        "KE" to listOf(local("Nation", "https://nation.africa", "https://nation.africa/kenya/rss.xml")),
        "GH" to listOf(local("MyJoyOnline", "https://www.myjoyonline.com", "https://www.myjoyonline.com/feed/")),
        "PK" to listOf(local("Dawn", "https://www.dawn.com", "https://www.dawn.com/feeds/home")),
        "AU" to listOf(local("The Guardian", "https://www.theguardian.com", "https://www.theguardian.com/australia-news/rss")),
        "IE" to listOf(local("RTÉ", "https://www.rte.ie", "https://www.rte.ie/feeds/rss/?index=/news/")),
        "NG" to listOf(local("Punch Newspapers", "https://punchng.com", "https://punchng.com/feed/")),
    )

    private fun world(name: String, site: String, feed: String) = Publisher(name, site, mapOf(TOP to feed))

    private fun local(name: String, site: String, feed: String, improveImage: (String) -> String = { it }) =
        Publisher(name, site, mapOf(TOP to feed), improveImage)

    /** Top stories from the country's own outlets; empty for countries without any listed. */
    suspend fun localHeadlines(countryCode: String): List<Article> =
        fetchAll(localPublishers[countryCode.uppercase()].orEmpty(), TOP)

    /**
     * Stories from the World outlets' [category] feeds that mention any of [terms] (a country's
     * name, adjective, capital) in the headline or summary. All of each feed is searched, not
     * just the few stories World shows, so most countries in the news get photo stories.
     */
    suspend fun mentioning(category: Category, terms: List<String>): List<Article> {
        if (terms.isEmpty()) return emptyList()
        val pattern = Regex(terms.joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(?:", postfix = ")(?![\\p{L}\\p{N}])") { Regex.escape(it) })
        return fetchAll(publishers, category, perPublisher = Int.MAX_VALUE)
            .filter { pattern.containsMatchIn(it.title) || it.description?.let(pattern::containsMatchIn) == true }
    }

    /**
     * Fetches every publisher's feed for [category] in parallel. One feed failing
     * doesn't break the tab. Each publisher contributes at most [PER_PUBLISHER]
     * stories, so outlets that post very often don't crowd out the rest.
     */
    suspend fun headlines(category: Category): List<Article> =
        // Top draws on about thirty outlets, so each gets fewer slots to leave room for all of them.
        fetchAll(publishers, category, perPublisher = if (category == TOP) PER_PUBLISHER_TOP else PER_PUBLISHER)

    private suspend fun fetchAll(
        publishers: List<Publisher>,
        category: Category,
        perPublisher: Int = PER_PUBLISHER,
    ): List<Article> = coroutineScope {
        if (publishers.isEmpty()) return@coroutineScope emptyList()
        val results = publishers.mapNotNull { publisher ->
            val url = publisher.feeds[category] ?: return@mapNotNull null
            async { runCatching { fetch(publisher, url) } }
        }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { result ->
            result.getOrDefault(emptyList()).sortedByDescending { it.publishedAt }.take(perPublisher)
        }
    }

    private suspend fun fetch(publisher: Publisher, url: String): List<Article> {
        val logo = logoUrlFor(publisher.site)
        return rss.fetch(url.toHttpUrl()).mapIndexed { i, item ->
            Article(
                title = item.title,
                description = item.description,
                url = item.link,
                imageUrl = item.imageUrl?.let(publisher.improveImage),
                logoUrl = logo,
                // Some feeds use <source> for photo credits, so always use the publisher's own name.
                sourceName = publisher.name,
                publishedAt = item.publishedAt,
                rank = i,
            )
        }
    }

    private companion object {
        const val PER_PUBLISHER = 10
        const val PER_PUBLISHER_TOP = 5
    }
}
