package com.example.newsly.data.cache

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** When a feed was last downloaded, and where its local stories were from. */
@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val feedKey: String,
    val fetchedAtMillis: Long,
    /** "Dhaka" / "Bangladesh" for local feeds; null for World. */
    val scope: String?,
    /** The location a local feed was fetched for, so a move to another city triggers a refresh. */
    val locationKey: String?,
)

@Entity(tableName = "feed_articles", primaryKeys = ["feedKey", "position"])
data class FeedArticleEntity(
    val feedKey: String,
    val position: Int,
    val url: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val logoUrl: String?,
    val sourceName: String,
    val publishedAtMillis: Long?,
)

@Dao
interface FeedCacheDao {
    @Query("SELECT * FROM feeds WHERE feedKey = :feedKey")
    suspend fun feed(feedKey: String): FeedEntity?

    @Query("SELECT * FROM feed_articles WHERE feedKey = :feedKey ORDER BY position")
    suspend fun articles(feedKey: String): List<FeedArticleEntity>

    /** Swaps in a freshly downloaded feed in one step, so readers never see half of it. */
    @Transaction
    suspend fun replace(feed: FeedEntity, articles: List<FeedArticleEntity>) {
        deleteArticles(feed.feedKey)
        insertFeed(feed)
        insertArticles(articles)
    }

    @Query("DELETE FROM feed_articles WHERE feedKey = :feedKey")
    suspend fun deleteArticles(feedKey: String)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: FeedEntity)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<FeedArticleEntity>)
}

/** A story's photo, found from its article page; null when the page has none. */
@Entity(tableName = "story_images")
data class StoryImageEntity(
    @PrimaryKey val articleUrl: String,
    val imageUrl: String?,
    val checkedAtMillis: Long,
)

@Dao
interface StoryImageDao {
    @Query("SELECT * FROM story_images WHERE articleUrl = :articleUrl")
    suspend fun find(articleUrl: String): StoryImageEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(image: StoryImageEntity)
}

/**
 * Last downloaded copy of each feed. Kept apart from bookmarks because it can
 * always be rebuilt from the network, so schema changes can simply wipe it.
 */
@Database(entities = [FeedEntity::class, FeedArticleEntity::class, StoryImageEntity::class], version = 2, exportSchema = false)
abstract class FeedCacheDatabase : RoomDatabase() {
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun storyImageDao(): StoryImageDao

    companion object {
        fun create(context: Context): FeedCacheDatabase =
            Room.databaseBuilder(context, FeedCacheDatabase::class.java, "feed_cache.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
