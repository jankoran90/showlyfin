package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.RssFeed
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline paměť Poslechu (user 2026-10-12, „musí fungovat i offline"): poslední úspěšně načtený
 * seznam zdrojů a díly každého zdroje. Bez sítě se z ní vykreslí Zdroje / Poslech dětí / Domů
 * a stažené díly jdou přehrát; dřív seznam bez sítě úplně zmizel. Soubory v `filesDir/source-cache`.
 * Díly se SLUČUJÍ (nové napřed, dřív známé zůstanou) — menší výřez feedu nesmí smazat starší díly.
 * Všechno na [Dispatchers.IO]; zápisy serializuje [writeLock] + jedinečný dočasný soubor.
 */
@Singleton
class SourceOfflineCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "source-cache")
    private val gson = Gson()
    private val writeLock = Mutex()

    suspend fun readSources(): List<PodcastSource>? =
        read("sources", object : TypeToken<List<PodcastSource>>() {})

    /** [current] se vyhodnotí až POD zámkem → poslední zápis vždy uloží nejnovější seznam. */
    suspend fun writeSources(current: () -> List<PodcastSource>) = update<Any>("sources", null) { current() }

    suspend fun readRss(feedUrl: String): RssFeed? = read("rss:$feedUrl", object : TypeToken<RssFeed>() {})

    suspend fun writeRss(feedUrl: String, feed: RssFeed) =
        update("rss:$feedUrl", object : TypeToken<RssFeed>() {}) { old ->
            if (old == null) feed else feed.copy(episodes = mergeById(feed.episodes, old.episodes) { it.id })
        }

    suspend fun readEpisodes(sourceKey: String): List<SourceEpisode>? =
        read("eps:$sourceKey", object : TypeToken<List<SourceEpisode>>() {})

    suspend fun writeEpisodes(sourceKey: String, episodes: List<SourceEpisode>) =
        update("eps:$sourceKey", object : TypeToken<List<SourceEpisode>>() {}) { old ->
            mergeById(episodes, old.orEmpty()) { it.id }
        }

    /** Nové v pořadí serveru + dřív známé, které v novém výřezu chybí (strop [MAX_EPISODES]). */
    private fun <T> mergeById(fresh: List<T>, old: List<T>, id: (T) -> String): List<T> {
        val seen = fresh.mapTo(HashSet(), id)
        return (fresh + old.filter { id(it) !in seen }).take(MAX_EPISODES)
    }

    private suspend fun <T> read(name: String, type: TypeToken<T>): T? = withContext(Dispatchers.IO) { readNow(name, type) }

    private fun <T> readNow(name: String, type: TypeToken<T>): T? = runCatching {
        val f = file(name)
        if (!f.exists()) null else gson.fromJson<T>(f.readText(), type.type)
    }.onFailure { Timber.w(it, "[OFFLINE] čtení cache '%s' selhalo", name) }.getOrNull()

    /** Čtení–sloučení–zápis CELÉ pod zámkem (souběžné výřezy téhož zdroje se navzájem nepřepíšou). */
    private suspend fun <T> update(name: String, type: TypeToken<T>?, transform: (T?) -> Any) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            runCatching {
                val value = transform(type?.let { readNow(name, it) })
                dir.mkdirs()
                val target = file(name)
                val tmp = File.createTempFile(target.nameWithoutExtension, ".tmp", dir)
                tmp.writeText(gson.toJson(value))
                if (!tmp.renameTo(target)) { tmp.delete(); error("rename selhal") }
            }.onFailure { Timber.w(it, "[OFFLINE] zápis cache '%s' selhal", name) }
        }
        Unit
    }

    private fun file(name: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(name.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private companion object {
        const val MAX_EPISODES = 1500
    }
}
