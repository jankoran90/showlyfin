package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.uploader.SourceOfflineCache
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slovo 2026-10-13 (user „po vypnutí wifi: Trabantem — nepodařilo se načíst díly pořadu, RSS jde") —
 * offline záloha obrazovek ČT pořadu a YouTube kanálu (RSS ji má v [PodcastSourcesRepository.loadRss]).
 * Online: poslední seznam dílů se uloží do [SourceOfflineCache] (stejné místo, kam ho ukládá Domů / Poslech
 * dětí). Offline: seznam z paměti + stažené díly toho pořadu, které v paměti chybí (stažené jdou vždy přehrát).
 */
@Singleton
class ListenOfflineEpisodes @Inject constructor(
    private val cache: SourceOfflineCache,
    private val offline: OfflineDownloadManager,
) {
    suspend fun save(sourceKey: String, episodes: List<SourceEpisode>) {
        if (episodes.isNotEmpty()) runCatching { cache.writeEpisodes(sourceKey, episodes) }
    }

    /** Díly pořadu bez sítě (prázdné = nic v paměti ani staženo). [keyPrefix] = `ctv:` / `yt:`. */
    suspend fun load(sourceKey: String, keyPrefix: String): List<SourceEpisode> {
        val cached = runCatching { cache.readEpisodes(sourceKey) }.getOrNull().orEmpty()
        val known = cached.mapTo(HashSet()) { it.id }
        val downloaded = offline.downloads.value
            .filter { it.sourceKey == sourceKey && it.key.startsWith(keyPrefix) }
            .map { d ->
                SourceEpisode(
                    id = d.key.removePrefix(keyPrefix),
                    title = d.title,
                    subtitle = d.subtitle,
                    streamUrl = "",
                    imageUrl = d.posterUrl,
                    date = d.publishedAt?.let(::isoDate),
                    resumeKey = d.key,
                    description = d.description,
                    durationSec = d.durationSec,
                    seriesTitle = d.seriesTitle,
                )
            }
            .filter { it.id !in known }
        return cached + downloaded
    }

    private fun isoDate(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(ms)
}
