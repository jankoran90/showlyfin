package com.github.jankoran90.showlyfin.feature.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-wide disk-backed media cache. ExoPlayer streams (Jellyfin + RD direct URLs)
 * are written to this LRU cache as they download, so connection jitter / brief drops
 * don't stall playback and seek-back replays from disk. This is a streaming buffer,
 * not an offline download (capped LRU, evicts oldest).
 *
 * Only one SimpleCache may exist per directory per process — hence the singleton.
 */
@OptIn(UnstableApi::class)
object PlaybackCache {
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.cacheDir, "media_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { instance = it }
        }
}
