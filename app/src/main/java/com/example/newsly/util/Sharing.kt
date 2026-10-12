package com.example.newsly.util

import android.content.Context
import android.content.Intent

/** Opens the system share sheet with the headline and link. */
fun Context.shareArticle(title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n$url\n\nvia Newsly")
    }
    startActivity(Intent.createChooser(send, "Share story"))
}
