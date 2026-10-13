package com.github.jankoran90.showlyfin.data.uploader.subtitle

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.ActiveTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
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
    }
}

/** „zbývá ~3 min" / „zbývá ~1 h 10 min" (null = zatím neznámo). */
fun etaLabel(etaS: Int?): String? {
    val s = etaS?.takeIf { it > 0 } ?: return null
    val min = (s + 59) / 60
    return if (min < 60) "zbývá ~$min min" else "zbývá ~${min / 60} h ${min % 60} min"
}
