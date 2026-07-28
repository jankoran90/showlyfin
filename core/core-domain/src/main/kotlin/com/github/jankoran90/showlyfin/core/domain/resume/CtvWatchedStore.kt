package com.github.jankoran90.showlyfin.core.domain.resume

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VLTAVA (SHW-110) F6c — DOKOUKANÉ díly ČT (klíč `ctv:<idec>`, viz `CTV_SCHEME`).
 *
 * Proč vlastní paměť: díl z ČT nemá Jellyfin id ani imdb, takže „zhlédnuto" nemá kam nahlásit
 * ([WatchedReporter] umí Jellyfin a Trakt). A z [VideoResumeStore] to poznat NEJDE — dokoukaný díl
 * má pozici smazanou stejně jako díl, který se nikdy nespustil. Bez tohohle rozlišení by řada
 * „Další díly" u pořadu napořád nabízela ten první (user 2026-07-28: „u ČT chci jít vždy od
 * nejstarších, stejně jako u seriálu od S01E01").
 */
@Singleton
class CtvWatchedStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ctv_watched", Context.MODE_PRIVATE)

    private val _watched = MutableStateFlow(load())

    /** Množina `ctv:<idec>` dokoukaných dílů; reaktivní (řada „Další díly" se posune sama). */
    val watched: StateFlow<Set<String>> = _watched.asStateFlow()

    fun isWatched(key: String): Boolean = key.isNotBlank() && key in _watched.value

    fun markWatched(key: String) {
        if (key.isBlank() || key in _watched.value) return
        val next = _watched.value + key
        _watched.value = next
        prefs.edit().putStringSet(KEY_SET, next).apply()
    }

    /** Ruční „nezhlédnuto" (kdyby si to user chtěl přehrát znovu od začátku seriálu). */
    fun clear(key: String) {
        if (key !in _watched.value) return
        val next = _watched.value - key
        _watched.value = next
        prefs.edit().putStringSet(KEY_SET, next).apply()
    }

    private fun load(): Set<String> = prefs.getStringSet(KEY_SET, emptySet())?.toSet() ?: emptySet()

    private companion object {
        const val KEY_SET = "ctv_watched_keys"
    }
}
