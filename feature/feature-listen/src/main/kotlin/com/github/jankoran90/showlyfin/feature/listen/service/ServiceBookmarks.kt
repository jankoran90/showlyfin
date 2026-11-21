package com.github.jankoran90.showlyfin.feature.listen.service

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.Bookmark
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.addAt
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.bookmarkTargetFromItem
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.formatBookmarkTime
import com.github.jankoran90.showlyfin.feature.listen.player.ListenPlayerPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AMPHORA (SHW-124) F5 / PR2 + IN2 — záložky ve službě přehrávače: dvojklik na sluchátkách, tlačítko
 * Záložka v Android Auto / notifikaci a karta Záložky v Autě. Vyčleněno z [AudiobookPlayerService]
 * (anti-monolit).
 */

/**
 * Tlačítko sluchátek (play/pause): jeden stisk = přehrát / pauza (po krátké prodlevě, jestli nepřijde
 * druhý), dvojklik = záložka. Vypnuto v Nastavení ([ListenPlayerPrefs.HEADSET_SYSTEM]) → chování systému.
 */
@UnstableApi
internal class HeadsetButtons(
    private val prefs: ListenPlayerPrefs,
    private val scope: CoroutineScope,
    private val player: () -> Player?,
    private val onDoubleTap: () -> Unit,
) {
    private var pendingSingle: Job? = null

    /** true = stisk vyřízen tady (Media3 ho už nezpracuje). */
    fun handle(intent: Intent): Boolean {
        if (prefs.headsetDoubleTap != ListenPlayerPrefs.HEADSET_BOOKMARK) return false
        val ev = keyEvent(intent) ?: return false
        if (ev.keyCode != KeyEvent.KEYCODE_HEADSETHOOK && ev.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
        if (ev.action != KeyEvent.ACTION_DOWN || ev.repeatCount > 0) return true
        val pending = pendingSingle
        if (pending != null && pending.isActive) {
            pending.cancel()
            pendingSingle = null
            onDoubleTap()
            return true
        }
        pendingSingle = scope.launch {
            delay(DOUBLE_TAP_MS)
            pendingSingle = null
            player()?.let { Util.handlePlayPauseButtonAction(it) }
        }
        return true
    }

    private fun keyEvent(intent: Intent): KeyEvent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)

    private companion object {
        const val DOUBLE_TAP_MS = 350L
    }
}

/** Záložka na právě hraném místě (sluchátka, Auto, notifikace). Potvrdí pípnutím a krátkou zprávou. */
internal fun AudiobookPlayerService.bookmarkFromPlayer(p: Player) {
    val item = p.currentMediaItem
    val chapter = currentPlayback?.chapters?.let { chs ->
        val offSec = item?.mediaMetadata?.extras?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0
        val posSec = offSec + p.currentPosition / 1000.0
        chs.lastOrNull { it.startSec <= posSec }?.title
    }
    val target = item?.let { bookmarkTargetFromItem(it, p.currentPosition, p.duration, chapter) }
    if (target == null) {
        toast("Tady záložku uložit nejde")
        return
    }
    val b = bookmarkStore.addAt(target)
    if (listenPrefs.bookmarkBeep) beep()
    toast("Záložka ${formatBookmarkTime(b.posMs)} · ${b.title}")
}

private fun AudiobookPlayerService.toast(text: String) {
    Handler(Looper.getMainLooper()).post { runCatching { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() } }
}

/** Krátké potvrzovací pípnutí přes právě hrající zvuk (sluchátka / auto). */
private fun beep() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
    }
}

/** Karta / složka Záložky v Autě: záložky profilu, nejnovější první. */
internal fun AudiobookPlayerService.bookmarkItems(): List<MediaItem> =
    bookmarkStore.bookmarks.value.map(::bookmarkItem)

/** Vstup „🔖 Záložky (N)“ na začátek Pokračovat, když Záložky nejsou samostatná karta. */
internal suspend fun AudiobookPlayerService.bookmarksEntry(): List<MediaItem> {
    // Jen když se karta Záložky opravdu vešla mezi zobrazené (limit karet, knihovny jako samostatné karty).
    if (AutoTabsPrefs.Tab.BOOKMARKS in autoTabs.tabs && rootChildren().any { it.mediaId == AudiobookPlayerService.NODE_BOOKMARKS }) return emptyList()
    val n = bookmarkStore.bookmarks.value.size
    return if (n == 0) emptyList() else listOf(browsableNode(AudiobookPlayerService.NODE_BOOKMARKS, "🔖 Záložky ($n)"))
}

/** Přehratelná položka záložky (`bmk:<id>`) — přehrání odtud řeší [resolveAutoRequest]. */
private fun bookmarkItem(b: Bookmark): MediaItem {
    val what = b.note.ifBlank { b.chapter ?: "" }
    return MediaItem.Builder()
        .setMediaId("${AudiobookPlayerService.PREFIX_BOOKMARK}${b.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(if (what.isBlank()) "${formatBookmarkTime(b.posMs)} · ${b.title}" else "${formatBookmarkTime(b.posMs)} · $what")
                .setSubtitle(b.title)
                .setArtist(b.title)
                .setArtworkUri(b.coverUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(if (b.isBook) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER else MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .build(),
        )
        .build()
}
