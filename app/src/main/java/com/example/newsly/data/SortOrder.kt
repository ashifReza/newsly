package com.example.newsly.data

/** How a feed's stories are ordered on screen. */
enum class SortOrder(val label: String, val description: String) {
    LATEST("Latest", "Newest stories first"),
    TOP_PICKS("Top picks", "Each outlet's leading stories first"),
    BY_OUTLET("By outlet", "Grouped by publisher, A to Z");

    fun apply(articles: List<Article>): List<Article> = when (this) {
        // Feeds are saved newest first, with a photo story leading when there is one.
        LATEST -> articles
        // Outlets take turns: every outlet's lead story, then every outlet's second story, and so on.
        TOP_PICKS -> articles.sortedWith(
            compareBy<Article> { it.rank ?: Int.MAX_VALUE }.thenByDescending { it.publishedAt }
        )
        BY_OUTLET -> articles.sortedWith(
            compareBy<Article, String>(String.CASE_INSENSITIVE_ORDER) { it.sourceName.removePrefix("The ") }
                .thenByDescending { it.publishedAt }
        )
    }
}
