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
)
