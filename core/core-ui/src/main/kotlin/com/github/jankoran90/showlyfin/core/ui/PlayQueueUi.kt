package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * RAMPA (SHW-121) — „je tenhle titul ve frontě K přehrání?" pro karty.
 *
 * User 2026-08-28: *„Chci taky vidět na coveru nějaky indikator že je v seznamu k prehrani."*
 * Stejná mechanika jako u odznaku „má uložený zdroj" ([LocalSourceAvailabilityProvider]) — karta se
 * neptá žádného ViewModelu, jen čte z provideru zavěšeného nad celou obrazovkou.
 */
interface PlayQueueProvider {
    /** Klíče titulů ve frontě ve tvaru `movie:<tmdbId>` / `show:<tmdbId>` (viz [playQueueKey]). */
    val queuedKeys: StateFlow<Set<String>>
}

val LocalPlayQueueProvider = staticCompositionLocalOf<PlayQueueProvider?> { null }

/**
 * Klíč titulu ve frontě. 🔴 Nese i TYP: tmdb id filmu a seriálu se překrývají (tmdb 30984 je seriál
 * Bleach i film „Dissection"), takže samotné číslo by značku pověsilo na cizí kartu.
 */
fun playQueueKey(tmdbId: Long?, isShow: Boolean): String? =
    tmdbId?.takeIf { it > 0L }?.let { (if (isShow) "show:" else "movie:") + it }

/** Je titul ve frontě? Bez provideru vždy false (karta pak značku prostě nekreslí). */
@Composable
fun rememberInQueue(tmdbId: Long?, isShow: Boolean): Boolean {
    val provider = LocalPlayQueueProvider.current ?: return false
    val keys by provider.queuedKeys.collectAsStateWithLifecycle()
    val key = playQueueKey(tmdbId, isShow) ?: return false
    return key in keys
}
