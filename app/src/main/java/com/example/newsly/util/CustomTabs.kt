package com.example.newsly.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Opens the publisher's page in a Chrome Custom Tab (falls back to any browser). */
fun Context.openArticle(url: String) {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(this, Uri.parse(url))
}
