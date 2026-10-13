package com.github.jankoran90.showlyfin.data.uploader.subtitle

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.ActiveTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AMFITEÁTR (FLM-05) F2 — stále viditelný přehled AI překladů. User: „ať je vidět progress bar i když
 * zavřu kartu nebo video … někde always visible, co se překládá, progress bar, ETA".
 *
 * Zdroj pravdy je server (`/api/subtitles/translate/active`) — vidí i překlady spuštěné z jiného
 * zařízení. Polling jen dokud někdo poslouchá (`WhileSubscribed`): rychle, když něco běží, pomalu, když ne.
 */
@Singleton
class ActiveTranslationsRepository @Inject constructor(
    private val remote: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val jobs: StateFlow<List<ActiveTranslation>> = flow {
        while (true) {
            val list = fetch()
            if (list != null) emit(list)
            delay(if (list.orEmpty().any { it.isActive }) POLL_ACTIVE_MS else POLL_IDLE_MS)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Úlohy, které user zavřel klepnutím (jen do zabití appky). */
    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Pro ukazatele: běžící + nedávno skončené (hotovo/chyba ještě [DONE_VISIBLE_S]), bez zavřených.
     * BUG 1.2.128 (proužek „Přeloženo…" visel pořád): stáří se filtrovalo jen při recompozici a StateFlow
     * stejný seznam znovu nevyšle → filtr žije tady a tikač ho přepočítává i bez nových dat ze serveru.
     */
    val visible: StateFlow<List<ActiveTranslation>> = combine(jobs, dismissed, ticker()) { list, gone, nowS ->
        list.filter { it.jobId !in gone && (it.isActive || isRecentlyFinished(it, nowS)) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismiss(jobId: String) = dismissed.update { it + jobId }

    private fun ticker() = flow {
        while (true) {
            emit(System.currentTimeMillis() / 1000)
            delay(TICK_MS)
        }
    }

    private fun isRecentlyFinished(job: ActiveTranslation, nowS: Long): Boolean {
        if (job.status != "done" && job.status != "error") return false
        val finished = job.finished ?: return false
        return nowS - finished < DONE_VISIBLE_S
    }

    private suspend fun fetch(): List<ActiveTranslation>? {
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        if (base.isBlank()) return null
        val cookie = prefs.getString("uploader_session_cookie", "").orEmpty()
        return try {
            remote.getActiveTranslations(base, cookie)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "[Lingua] přehled překladů nedostupný")
            null
        }
    }

    private companion object {
        const val POLL_ACTIVE_MS = 4_000L
        const val POLL_IDLE_MS = 20_000L
        const val TICK_MS = 10_000L
        const val DONE_VISIBLE_S = 120L
    }
}

/** „zbývá ~3 min" / „zbývá ~1 h 10 min" (null = zatím neznámo). */
fun etaLabel(etaS: Int?): String? {
    val s = etaS?.takeIf { it > 0 } ?: return null
    val min = (s + 59) / 60
    return if (min < 60) "zbývá ~$min min" else "zbývá ~${min / 60} h ${min % 60} min"
}
