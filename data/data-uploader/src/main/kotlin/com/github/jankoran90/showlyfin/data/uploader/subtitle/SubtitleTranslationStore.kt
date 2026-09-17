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
        /** [jobId]/[ok]/[total] (PROGRESSIVE, 2026-09-16): server ukládá po KAŽDÉ dávce průběžně,
         *  takže [jobId] je stažitelné (rozpracovaný obsah) i během "running" — VM z toho dělá
         *  živý progress text a speculativně nasazuje/přenačítá stopu, ať se přeložené řádky
         *  objevují v přehrávači průběžně, ne až na dvou pevných zastávkách. */
        data class Running(val jobId: String? = null, val ok: Int = 0, val total: Int = 0) : State
        /** VLNY (2026-09-18): server sám přeložil, kolik šlo pod limitem kvóty (auto-chain vln),
         *  obsah je stažitelný HNED — teď čeká na explicitní potvrzení pokračování i přes riziko
         *  ([SubtitleTranslationStore.enqueueContinueTranslate]), protože 5h mozek kvóta je na/nad
         *  limitem z Nastavení. [quotaPct]/[avgWavePct] = živá kvóta a odhad spotřeby na další vlnu. */
        data class PausedQuota(val subId: String, val quotaPct: Float?, val avgWavePct: Float?) : State
        data class Done(val subId: String) : State
        data class Error(val message: String) : State
    }

    private val _jobs = MutableStateFlow<Map<String, State>>(emptyMap())
    val jobs: StateFlow<Map<String, State>> = _jobs.asStateFlow()

    /** Klíč filmu/epizody — shodný s tím, jak server cachuje a jak VM páruje stopu. */
    fun keyOf(imdb: String, season: Int?, episode: Int?): String =
        if (season != null && episode != null) "${imdb}_s${season}e$episode" else imdb

    fun setRunning(key: String) = _jobs.update { it + (key to State.Running()) }

    /** Živý progress tik během "running" (PROGRESSIVE) — [jobId] = subId, jakmile server nahlásí
     *  aspoň 1 hotovou dávku (dřív je null, nic ke stažení). Volá [SubtitleTranslateWorker] po
     *  každém pollu, dokud status zůstává "running". */
    fun updateRunningProgress(key: String, jobId: String?, ok: Int, total: Int) =
        _jobs.update { it + (key to State.Running(jobId, ok, total)) }

    fun setError(key: String, message: String) = _jobs.update { it + (key to State.Error(message)) }

    /** Hotovo: zapiš subId trvale (auto-nasazení po návratu) + vystav v živém flow. */
    fun markDone(key: String, subId: String) {
        prefs.edit { putString(PREFIX + key, subId); putBoolean(PARTIAL_PREFIX + key, false) }
        _jobs.update { it + (key to State.Done(subId)) }
    }

    /** Pauza na kvótě (VLNY) — subId je stejné jako u pozdějšího `markDone` (job_id se nemění), takže
     *  existující kandidát v přehrávači se při dokončení jen vynuceně přenačte, ne nahradí. Persistuje
     *  se stejně jako `markDone` (obsah je stažitelný hned) + příznak [isPausedForQuota], ať appka po
     *  návratu na obrazovku ví nabídnout „Pokračovat i přes riziko". */
    fun markPausedQuota(key: String, subId: String, quotaPct: Float?, avgWavePct: Float?) {
        prefs.edit { putString(PREFIX + key, subId); putBoolean(PARTIAL_PREFIX + key, true) }
        _jobs.update { it + (key to State.PausedQuota(subId, quotaPct, avgWavePct)) }
    }

    /** Persistovaný výsledek dřívějšího překladu (přežije restart appky) — null = ještě nepřeloženo. */
    fun doneSubId(key: String): String? =
        prefs.getString(PREFIX + key, null)?.takeIf { it.isNotBlank() }

    /**
     * OKAPI (2026-09-10, user: "All the Long Nights" — appka x62 zopakovala stažení AI titulku
     * s HTTP 404, nikdy nenabídla překlad znovu): server ztratil cache souboru AI překladu
     * (potvrzeno migrací `/upload_temp` storage — appka si ale pořád myslí "hotovo" a stahuje
     * navěky mrtvé ID. Zapomeň persistovaný výsledek → příští otevření filmu nabídne překlad
     * znovu (nový, funkční `ai_<hash>` soubor).
     */
    fun clearDone(key: String) {
        prefs.edit { remove(PREFIX + key) }
        _jobs.update { it - key }
    }

    /** True = [doneSubId] existuje, ale zbytek čeká na potvrzení pokračování přes kvótu (VLNY). */
    fun isPausedForQuota(key: String): Boolean = prefs.getBoolean(PARTIAL_PREFIX + key, false)

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
        progressive: Boolean = false,
        model: String? = null,
        quotaLimit: Float? = null,
    ) = SubtitleTranslateWorker.enqueue(context, base, cookie, imdb, title, season, episode, progressive, model, quotaLimit)

    /** VLNY (2026-09-18): pokračování pozastaveného LINGUA-YT jobu po odsouhlasení kvótového varování
     *  usera (VŽDY confirm=true — appka tuhle cestu volá jen z toho dialogu, nikdy automaticky).
     *  [jobId] = subId z [State.PausedQuota]. */
    fun enqueueContinueTranslate(
        context: Context,
        base: String,
        cookie: String,
        jobId: String,
        key: String,
        title: String,
    ) = SubtitleTranslateWorker.enqueueContinue(context, base, cookie, jobId, key, title)

    private companion object {
        const val PREFIX = "ai_sub_"
        const val PARTIAL_PREFIX = "ai_sub_partial_"
    }
}
