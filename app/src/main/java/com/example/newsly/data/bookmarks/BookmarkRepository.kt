package com.example.newsly.data.bookmarks

import com.example.newsly.data.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class BookmarkRepository(private val dao: BookmarkDao) {

    /** Saved stories, most recently saved first. */
    val bookmarks: Flow<List<Article>> = dao.observeAll().map { list -> list.map { it.toArticle() } }

    suspend fun save(article: Article) =
        dao.insert(article.toEntity(savedAtMillis = System.currentTimeMillis()))

    /** Removes a bookmark and returns it, so the removal can be undone with [restore]. */
    suspend fun remove(url: String): BookmarkEntity? =
        dao.find(url)?.also { dao.delete(url) }

    suspend fun restore(bookmark: BookmarkEntity) = dao.insert(bookmark)

    private fun Article.toEntity(savedAtMillis: Long) = BookmarkEntity(
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
        logoUrl = logoUrl,
        sourceName = sourceName,
        publishedAtMillis = publishedAt?.toEpochMilli(),
        savedAtMillis = savedAtMillis,
    )

    private fun BookmarkEntity.toArticle() = Article(
        title = title,
        description = description,
        url = url,
        imageUrl = imageUrl,
        logoUrl = logoUrl,
        sourceName = sourceName,
        publishedAt = publishedAtMillis?.let(Instant::ofEpochMilli),
    )
}
