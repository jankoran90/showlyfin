package com.github.jankoran90.showlyfin.ui.tv.cast

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * FILMYCAST — TV přijímač castu telefon→TV do Filmy appky. Telefon zařadí přehrávací příkaz do serverové
 * fronty (pod aktivním profilem = jellyfinUserId); TV shell frontu pollí a při čekajícím příkazu skočí do
 * přehrávače. Fronta je POP (backend vrátí+smaže) → příkaz se spotřebuje jednou.
 *
 * Poll běží JEN když je appka v popředí (RESUMED) a je aktivní profil — [TvCastReceiver] to řídí přes
 * `repeatOnLifecycle`. Interval ~4 s. Bez `sourceUrl` (jen imdb) příkaz zatím přeskočíme (TV-side dohledání
 * zdroje = follow-up); primární cesta = telefon posílá už resolvnutou přehratelnou URL.
 */
@HiltViewModel
class TvCastReceiverViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** POP jeden čekající příkaz pro aktivní profil. null = nic nečeká / offline / bez profilu / bez zdroje. */
    suspend fun poll(): TvCastCommand? {
        val profile = prefs.getString("jellyfin_user_id", "").orEmpty()
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        val cookie = prefs.getString("uploader_session_cookie", "").orEmpty()
        if (profile.isBlank() || base.isBlank()) return null
        val raw = runCatching { uploaderDs.castCommandGet(base, cookie, profile) }.getOrNull() ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (!json.optBoolean("pending", false)) return null
        val sourceUrl = json.optString("sourceUrl").takeIf { it.isNotBlank() }
        val title = json.optString("title").ifBlank { "Přehrávání" }
        if (sourceUrl == null) {
            // Follow-up: TV-side dohledání zdroje z imdb. Zatím jen zaznamenej a přeskoč (příkaz je už POPnutý).
            Timber.i("[FILMYCAST] příkaz bez sourceUrl (title=%s) — přeskočeno (TV-side resolve = follow-up)", title)
            return null
        }
        val imdb = json.optString("imdb")
        val year = json.optInt("year").takeIf { it > 0 }
        val subHint = json.optString("subtitleQuery").takeIf { it.isNotBlank() }
        // TV má imdb/title/year z příkazu → poskládá SubtitleQuery pro auto-dohledání CZ titulků; subtitleQuery
        // z příkazu je originální/hledací název (origTitle).
        val subtitleQuery = if (imdb.isNotBlank() || title.isNotBlank()) {
            SubtitleQuery(imdb = imdb, title = title, origTitle = subHint ?: title, year = year)
        } else null
        return TvCastCommand(
            sourceUrl = sourceUrl,
            title = title,
            subtitleQuery = subtitleQuery,
            posterUrl = json.optString("posterUrl").takeIf { it.isNotBlank() },
            positionMs = json.optLong("positionMs", 0L),
        )
    }
}

/** Jeden přehrávací příkaz castu (už resolvnutá URL připravená telefonem). */
data class TvCastCommand(
    val sourceUrl: String,
    val title: String,
    val subtitleQuery: SubtitleQuery? = null,
    val posterUrl: String? = null,
    val positionMs: Long = 0L,
)

/**
 * Poller castu — bezpečně pověšený na lifecycle: `repeatOnLifecycle(RESUMED)` běží jen s appkou v popředí a
 * sám se zruší při odchodu na pozadí. Umísti jednou na úroveň navigace (TvNavigator), ať pollí napříč
 * obrazovkami. Při čekajícím příkazu zavolá [onCast] (typicky navigace do přehrávače).
 */
@Composable
fun TvCastReceiver(
    onCast: (TvCastCommand) -> Unit,
    vm: TvCastReceiverViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCast by rememberUpdatedState(onCast)
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                runCatching { vm.poll() }.getOrNull()?.let { currentOnCast(it) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}

private const val POLL_INTERVAL_MS = 4_000L
