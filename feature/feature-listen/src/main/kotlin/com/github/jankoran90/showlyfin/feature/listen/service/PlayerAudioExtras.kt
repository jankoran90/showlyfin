package com.github.jankoran90.showlyfin.feature.listen.service

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.github.jankoran90.showlyfin.core.domain.audio.AudioBoost
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.feature.listen.player.ListenPlayerPrefs
import timber.log.Timber

/**
 * AMPHORA (SHW-124) F4 / PR1 — doplňky zvuku přímo na ExoPlayeru služby (platí i pro notifikaci,
 * sluchátka a Android Auto, nejen pro appku):
 *  - **přetočení po pauze**: po pauze / přerušení (hovor) přetočí zpět podle délky pauzy, max
 *    [ListenPlayerPrefs.rewindMaxSec]; ruční posun nebo nová položka v pauze přetočení zruší,
 *  - **zesílení hlasu pro pořad / knihu**: uložená úroveň pořadu z klíče v extras položky ([KEY_SHOW_KEY]) nebo
 *    z broadcastu po přepnutí v přehrávači, jinak globální DRC z Nastavení,
 *  - **přeskočení ticha** ([ListenPlayerPrefs.skipSilence]).
 */
internal class PlayerAudioExtras(
    private val player: ExoPlayer,
    private val audioBoost: () -> AudioBoost?,
    private val absPrefs: AbsPreferences,
    private val prefs: ListenPlayerPrefs,
) : Player.Listener {

    private var pausedAt: Long? = null
    private var lastMediaId: String? = null
    private var lastShowKey: String? = null
    private var showBoost: Int? = null

    /** Znovu použij nastavení (start služby, změna v Nastavení nebo v přehrávači). */
    fun apply(intent: Intent? = null) {
        if (intent?.hasExtra(ListenPlayerPrefs.EXTRA_SHOW_BOOST) == true) {
            showBoost = intent.getIntExtra(ListenPlayerPrefs.EXTRA_SHOW_BOOST, -1).takeIf { it >= 0 }
        }
        audioBoost()?.apply(showBoost ?: absPrefs.listenDrcLevel, AudioBoost.Profile.LISTEN)
        runCatching { player.skipSilenceEnabled = prefs.skipSilence }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) markPaused() else rewindIfPaused()
    }

    override fun onPlaybackSuppressionReasonChanged(reason: Int) {
        // Hovor / přechodná ztráta zvuku: playWhenReady zůstává true, hraní stojí.
        if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) markPaused()
        else if (player.playWhenReady) rewindIfPaused()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem?.mediaId != lastMediaId) {
            lastMediaId = mediaItem?.mediaId
            pausedAt = null // nová položka / další soubor knihy → žádné přetočení
        }
        val extras = mediaItem?.mediaMetadata?.extras
        val showKey = extras?.getString(KEY_SHOW_KEY)
        if (showKey != lastShowKey) {
            lastShowKey = showKey
            // Úroveň vždy z uložené volby pořadu (in-app i Android Auto položky nesou jen klíč).
            showBoost = prefs.boostFor(showKey)
            apply()
        }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // Ruční posun v pauze = user si místo vybral sám.
        if (reason == Player.DISCONTINUITY_REASON_SEEK && !player.playWhenReady) pausedAt = null
    }

    private fun markPaused() {
        if (pausedAt == null && player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED) {
            pausedAt = SystemClock.elapsedRealtime()
        }
    }

    private fun rewindIfPaused() {
        val at = pausedAt ?: return
        pausedAt = null
        if (!prefs.rewindAfterPause) return
        val back = rewindMs(SystemClock.elapsedRealtime() - at, prefs.rewindMaxSec * 1000L)
        if (back <= 0L) return
        // Vícesouborová kniha: pozice v čase celé knihy (offset souboru + pozice), přetočení smí přejít
        // do předchozího souboru (revize F4).
        val idx = player.currentMediaItemIndex
        val bookPos = offsetMs(idx) + player.currentPosition
        val target = (bookPos - back).coerceAtLeast(0L)
        var tIdx = idx
        while (tIdx > 0 && offsetMs(tIdx) > target) tIdx--
        Timber.i("[PR1] po pauze přetočeno o %d ms", bookPos - target)
        if (tIdx == idx) player.seekTo((target - offsetMs(idx)).coerceAtLeast(0L))
        else player.seekTo(tIdx, (target - offsetMs(tIdx)).coerceAtLeast(0L))
    }

    private fun offsetMs(index: Int): Long {
        if (index < 0 || index >= player.mediaItemCount) return 0L
        val sec = player.getMediaItemAt(index).mediaMetadata.extras
            ?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0
        return (sec * 1000).toLong()
    }

    companion object {
        /** Extras položky: klíč pořadu / knihy (zesílení se čte z [ListenPlayerPrefs.boostFor]). */
        const val KEY_SHOW_KEY = "slovo_show_key"

        /** Pauza < 10 s nic; do 1 min 1/6 maxima, do 5 min 1/3, do 30 min 2/3, déle celé maximum. */
        fun rewindMs(pauseMs: Long, maxMs: Long): Long = when {
            pauseMs < 10_000L -> 0L
            pauseMs < 60_000L -> maxMs / 6
            pauseMs < 5 * 60_000L -> maxMs / 3
            pauseMs < 30 * 60_000L -> maxMs * 2 / 3
            else -> maxMs
        }
    }
}
