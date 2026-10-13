package com.example.newsly.data.remote

import com.example.newsly.data.cache.StoryImageDao
import com.example.newsly.data.cache.StoryImageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a photo for publisher stories whose feed has none (Al Jazeera) by reading the
 * article page's preview image (`og:image`), the same one chat apps show for links.
 * Results, including "no photo", are saved, so each story is looked up once.
 */
class StoryImageFinder(
    private val client: OkHttpClient,
    private val dao: StoryImageDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Article URL → photo URL, or "" when the story has none. */
    private val known = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()
    /** A few lookups at a time, so scrolling quickly doesn't flood publishers. */
    private val permits = Semaphore(4)

    /**
     * Only links that already point at the publisher. Google News links would first have to be
     * turned into publisher links through Google, which treats that as bot traffic and blocks it.
     */
    fun canFind(articleUrl: String): Boolean =
        articleUrl.startsWith("https://") && !articleUrl.startsWith("https://news.google.com/")

    /** The photo if it's already known, without any network access. */
    fun cached(articleUrl: String): String? = known[articleUrl]?.ifEmpty { null }

    /**
     * Looks up the photo for [articleUrl]. The lookup carries on if the caller is
     * cancelled (the card scrolled away), so the result is ready when it comes back.
     */
    suspend fun find(articleUrl: String): String? {
        known[articleUrl]?.let { return it.ifEmpty { null } }
        return inFlight.computeIfAbsent(articleUrl) { url ->
            scope.async {
                try {
                    lookUp(url)
                } finally {
                    inFlight.remove(url)
                }
            }
        }.await()
    }

    private suspend fun lookUp(articleUrl: String): String? {
        dao.find(articleUrl)?.let { saved ->
            known[articleUrl] = saved.imageUrl.orEmpty()
            return saved.imageUrl
        }
        val image = runCatching {
            permits.withPermit {
                previewImage(articleUrl)
            }
        }.getOrElse { return null } // Network trouble: don't remember, try again next time.
        known[articleUrl] = image.orEmpty()
        dao.insert(StoryImageEntity(articleUrl, image, System.currentTimeMillis()))
        return image
    }

    /** The page's og:image (or twitter:image), resolved against the page URL. */
    private fun previewImage(pageUrl: String): String? {
        val html = get(pageUrl, limit = 400_000)
        val base = pageUrl.toHttpUrlOrNull() ?: return null
        val image = META_TAG.findAll(html).firstNotNullOfOrNull { tag ->
            val key = (attribute(tag.value, "property") ?: attribute(tag.value, "name"))?.lowercase()
            if (key in IMAGE_KEYS) attribute(tag.value, "content") else null
        } ?: return null
        return base.resolve(unescape(image).trim())?.toString()?.takeIf { it.startsWith("http") }
    }

    /** Downloads up to [limit] bytes of a page as text; the <head> is near the top. */
    private fun get(url: String, limit: Long): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val source = response.body?.source() ?: error("Empty")
            val buffer = Buffer()
            while (buffer.size < limit && source.read(buffer, 8192) != -1L) Unit
            buffer.readUtf8()
        }
    }

    private fun attribute(html: String, name: String): String? =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&#x2F;", "/").replace("&#47;", "/").replace("&quot;", "\"")

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        val META_TAG = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
        val IMAGE_KEYS = setOf("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")
    }
}
