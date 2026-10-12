package com.example.newsly.util

import android.content.Context
import android.content.Intent

fun Context.shareArticle(title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    }
    startActivity(Intent.createChooser(send, null))
}
