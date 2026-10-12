package com.example.newsly.data

/**
 * News categories shown as chips. [localQuery] is the Google News search used
 * for the Near you tab, where there are no ready-made category feeds.
 */
enum class Category(val label: String, val localQuery: String?) {
    TOP("Top", null),
    BUSINESS("Business", "business OR economy OR market"),
    TECHNOLOGY("Tech", "technology OR tech OR startup"),
    SCIENCE("Science", "science OR research OR climate"),
    HEALTH("Health", "health OR hospital OR disease"),
    SPORTS("Sports", "sports OR cricket OR football"),
    ENTERTAINMENT("Entertainment", "film OR music OR celebrity OR entertainment"),
}
