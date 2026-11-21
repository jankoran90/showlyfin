package com.github.jankoran90.showlyfin.feature.listen.player

import android.os.Bundle
import com.github.jankoran90.showlyfin.feature.listen.service.PlayerAudioExtras

/**
 * AMPHORA (SHW-124) F4 / PR1 — doplňky [AudiobookPlayerConnection] (vyčleněno, soubor je na hraně
 * velikosti): klíč pořadu / knihy pro zesílení hlasu, rychlost při spuštění a „Usnul jsi v…“.
 */

/** Klíč pořadu (díl) nebo knihy pro volby „jen pro tento pořad“. null = nelze určit. */
internal fun showKeyFor(episode: QueuedEpisode?, itemId: String?): String? = when {
    episode != null -> episode.itemId.takeIf { it.isNotBlank() && it != "search" }?.let { "show:$it" }
    !itemId.isNullOrBlank() -> "book:$itemId"
    else -> null
}

internal fun AudiobookPlayerConnection.currentShowKey(): String? =
    showKeyFor(currentEpisode, _state.value.currentItemId)

/** Klíč položky pro „Usnul jsi v…“ (díl nebo kniha). */
internal fun AudiobookPlayerConnection.wakeKey(): String? =
    currentEpisode?.episodeId?.takeIf { it.isNotBlank() } ?: _state.value.currentItemId?.takeIf { it.isNotBlank() }

/** Extras položky pro službu: klíč pořadu a jeho zesílení (služba je čte při načtení položky). */
internal fun Bundle.putShowExtras(showKey: String?, prefs: ListenPlayerPrefs) {
    if (showKey == null) return
    putString(PlayerAudioExtras.KEY_SHOW_KEY, showKey)
}

/** Rychlost při spuštění: výchozí z Nastavení (user 17:36 „rychlost vždy normální“), poslední jen na přání. */
internal fun AudiobookPlayerConnection.startSpeed(podcast: Boolean): Float {
    val s = if (listenPrefs.rememberLastSpeed) {
        if (podcast) prefs.lastPodcastSpeed else prefs.lastBookSpeed
    } else prefs.defaultSpeed
    return s.coerceIn(0.5f, 3.5f)
}

/** Nabídka návratu po spánku: jen pro právě hranou položku, když od té doby uběhlo > 30 s poslechu. */
internal fun AudiobookPlayerConnection.wakeOffer(posMs: Long): ListenPlayerPrefs.WakeMark? {
    val m = listenPrefs.wakeMark ?: return null
    if (System.currentTimeMillis() - m.atMs > WAKE_VALID_MS) {
        listenPrefs.wakeMark = null
        return null
    }
    return m.takeIf { it.key == wakeKey() && posMs > it.posMs + 30_000L }
}

/** „Vrátit“ — skok na poslední místo, kde jsi ještě nespal. */
fun AudiobookPlayerConnection.returnToWakeMark() {
    val m = listenPrefs.wakeMark ?: return
    listenPrefs.wakeMark = null
    if (m.key == wakeKey()) seekTo(m.posMs)
}

fun AudiobookPlayerConnection.dismissWakeMark() {
    listenPrefs.wakeMark = null
}

/** Zesílení hlasu jen pro právě hraný pořad / knihu; null = jako v Nastavení. */
fun AudiobookPlayerConnection.setShowBoost(level: Int?) {
    val key = currentShowKey() ?: return
    listenPrefs.setBoostFor(key, level)
    ListenPlayerPrefs.notifyService(context, level ?: -1)
}

/** Přeskočení ticha (globálně) — služba si volbu přečte hned. */
fun AudiobookPlayerConnection.setSkipSilence(on: Boolean) {
    listenPrefs.skipSilence = on
    ListenPlayerPrefs.notifyService(context)
}

private const val WAKE_VALID_MS = 36 * 60 * 60 * 1000L
