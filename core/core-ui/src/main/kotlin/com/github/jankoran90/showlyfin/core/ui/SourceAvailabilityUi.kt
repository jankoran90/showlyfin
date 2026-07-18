package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * CINEMATHEQUE (SHW-98, user 2026-07-18) — poskytovatel příznaku „titul má zapamatovaný zdroj přehrávání"
 * pro karty/řádky (vzor [LocalUserRatingProvider]). Wired JEDNOU v shellu (napojení na `WorkingSourceStore`);
 * [MediaRow]/[MediaCard] přes [rememberHasSource] vykreslí decentní odznak. Reaktivní — jak backfill uloží
 * zdroj, odznak naskočí bez překreslení sekce. Bez providera (TV shell / starý kód) = odznak se prostě
 * nezobrazí (default null).
 */
interface SourceAvailabilityProvider {
    /** Množina klíčů titulů s uloženým zdrojem, formát `tmdb:<id>` / `imdb:<tt…>` (== `WorkingSourceStore.savedKeys`). */
    val savedKeys: StateFlow<Set<String>>
}

val LocalSourceAvailabilityProvider = staticCompositionLocalOf<SourceAvailabilityProvider?> { null }

/** True když má titul zapamatovaný zdroj (podle tmdb NEBO imdb). Reaktivní na změnu uložených zdrojů. */
@Composable
fun rememberHasSource(tmdbId: Long?, imdbId: String?): Boolean {
    val provider = LocalSourceAvailabilityProvider.current ?: return false
    val keys by provider.savedKeys.collectAsStateWithLifecycle()
    val t = tmdbId?.takeIf { it > 0L }?.let { "tmdb:$it" }
    val i = imdbId?.takeIf { it.isNotBlank() }?.let { "imdb:$it" }
    return (t != null && t in keys) || (i != null && i in keys)
}
