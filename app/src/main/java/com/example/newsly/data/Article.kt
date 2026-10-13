package com.example.newsly.data

import java.time.Instant

/** The app's own article model, independent of whichever news API backs it. */
data class Article(
    val title: String,
    val description: String?,
    val url: String,
    /** Story photo, when the feed provides one. */
    val imageUrl: String?,
    /** Small publisher logo shown next to the source name. */
    val logoUrl: String?,
    val sourceName: String,
    val publishedAt: Instant?,
    /**
     * Position in its outlet's own feed (0 = the outlet's lead story), for "Top picks".
     * Null for stories that weren't read from a feed, such as old bookmarks.
     */
    val rank: Int? = null,
)
