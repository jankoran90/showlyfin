package com.github.jankoran90.showlyfin.data.uploader.subtitle

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Plan LINGUA Fáze 3 — sdílený stav AI překladu titulků mezi [SubtitleTranslateWorker] (běží na
 * pozadí, přežije odchod z přehrávače) a `PlaybackViewModel` (live update na otevřené obrazovce).
 *
 * Dvě úrovně paměti:
 *  - **[jobs]** = živý StateFlow stavů per film (Running/Done/Error). Worker i VM jsou ve STEJNÉM
 *    procesu → @Singleton instance je sdílená → VM reaguje okamžitě, dokud je obrazovka otevřená.
 *  - **persist `ai_sub_<key>`** v trakt prefs = přežije i smrt procesu → po návratu k filmu se AI
 *    čeština nasadí sama, bez ťuknutí ([doneSubId]). To je „ať nezmizí nikdy" (přání usera 2026-06-15).
 */
@Singleton
class SubtitleTranslationStore @Inject constructor(
    // Vlastní prefs (subtitle_prefs), NE trakt_prefs — ty maže Trakt logout. Viz AppModule.
    @Named("subtitlePreferences") private val prefs: SharedPreferences,
) {
    sealed interface State {
        data object Running : State
        data class Done(val subId: String) : State
        data class Error(val message: String) : State
    }

    private val _jobs = MutableStateFlow<Map<String, State>>(emptyMap())
    val jobs: StateFlow<Map<String, State>> = _jobs.asStateFlow()

    /** Klíč filmu/epizody — shodný s tím, jak server cachuje a jak VM páruje stopu. */
    fun keyOf(imdb: String, season: Int?, episode: Int?): String =
        if (season != null && episode != null) "${imdb}_s${season}e$episode" else imdb

    fun setRunning(key: String) = _jobs.update { it + (key to State.Running) }

    fun setError(key: String, message: String) = _jobs.update { it + (key to State.Error(message)) }

    /** Hotovo: zapiš subId trvale (auto-nasazení po návratu) + vystav v živém flow. */
    fun markDone(key: String, subId: String) {
        prefs.edit { putString(PREFIX + key, subId) }
        _jobs.update { it + (key to State.Done(subId)) }
    }

    /** Persistovaný výsledek dřívějšího překladu (přežije restart appky) — null = ještě nepřeloženo. */
    fun doneSubId(key: String): String? =
        prefs.getString(PREFIX + key, null)?.takeIf { it.isNotBlank() }

    /** Zařadí překlad na pozadí. Drží WorkManager (androidx.work) uvnitř `data-uploader`, aby se
     *  typy workeru neprolínaly do feature modulů — ty volají jen tohle. */
    fun enqueueTranslate(
        context: Context,
        base: String,
        cookie: String,
        imdb: String,
        title: String,
        season: Int?,
        episode: Int?,
    ) = SubtitleTranslateWorker.enqueue(context, base, cookie, imdb, title, season, episode)

    private companion object {
        const val PREFIX = "ai_sub_"
    }
}
