package com.github.jankoran90.showlyfin.feature.listen.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.github.jankoran90.showlyfin.core.domain.InstallGuard
import com.github.jankoran90.showlyfin.core.domain.audio.AudioBoost
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.feature.listen.R
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Dedikovaný audio přehrávač pro poslechovou sekci (audioknihy). Od Plan AUTOBAHN je to
 * [MediaLibraryService] (ne jen MediaSessionService) → kromě background audia + lock-screen
 * navíc vystavuje **browse strom** pro Android Auto (procházení ABS audioknih v autě), resolver
 * mediaId → reálný multi-track playlist a **custom akce** (±kapitola, rychlost, sleep timer) +
 * uzel „Kapitoly" pro skok na kapitolu z auta. In-app přehrávač (AudiobookPlayerConnection)
 * funguje beze změny — jeho položky (s URI) projdou resolverem nezměněné.
 *
 * Video (Jellyfin) se v Android Auto NEzobrazuje (projekce video nevykreslí) → Auto = jen ABS.
 *
 * Periodicky synchronizuje pozici zpět na ABS (sessionId + duration jsou v extras MediaItemu).
 *
 * Anti-monolit: Android Auto browse strom = [AudiobookBrowseTree] (extension funkce), buildery
 * položek = [AudiobookAutoMedia] (top-level internal). Tady zůstává player/session/notifikace/sync.
 */
@AndroidEntryPoint
class AudiobookPlayerService : MediaLibraryService() {

    @Inject lateinit var repo: AbsRepository
    @Inject lateinit var absPrefs: com.github.jankoran90.showlyfin.data.abs.AbsPreferences

    /** CRUISE (SHW-70): custom zdroje Poslechu (YouTube/RSS/NaVýbornou) do Android Auto browse stromu. */
    @Inject lateinit var sourcesRepo: PodcastSourcesRepository

    /** CRUISE (SHW-70): sdílená pozice resume direct epizod (RSS/YT) — zápis z auta + AA „Pokračovat". */
    @Inject lateinit var directResume: DirectResumeStore

    private var session: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var metaJob: Job? = null
    private var sleepJob: Job? = null

    /** Plan EVEN — DRC/normalizér napojený na audio session id přehrávače. */
    private var audioBoost: AudioBoost? = null

    /** Reaguje na změnu úrovně DRC v Nastavení → přepne efekt za běhu. */
    private val drcReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            audioBoost?.apply(absPrefs.listenDrcLevel, AudioBoost.Profile.LISTEN)
        }
    }

    /** Cache položek vrácených v onGetChildren (pro onGetItem). Přístup jen z main threadu. Internal =
     *  browse strom v [AudiobookBrowseTree] (directSourceEpisodes) do ní zapisuje. */
    internal val itemCache = HashMap<String, MediaItem>()

    /** Právě otevřená kniha (pro kapitoly v Auto + ±kapitola tlačítka). Jen main thread. Internal =
     *  čte ji browse strom v [AudiobookBrowseTree]. */
    internal var currentPlayback: AbsPlayback? = null

    // ---- Custom session commands (Android Auto / notifikace) ----
    private val cmdPrevChapter = SessionCommand(ACTION_PREV_CHAPTER, Bundle.EMPTY)
    private val cmdNextChapter = SessionCommand(ACTION_NEXT_CHAPTER, Bundle.EMPTY)
    private val cmdSpeed = SessionCommand(ACTION_SPEED, Bundle.EMPTY)
    private val cmdSleep = SessionCommand(ACTION_SLEEP, Bundle.EMPTY)

    private val customLayout: ImmutableList<CommandButton> by lazy {
        ImmutableList.of(
            commandButton(cmdPrevChapter, "Předchozí kapitola", R.drawable.ic_auto_prev_chapter),
            commandButton(cmdNextChapter, "Další kapitola", R.drawable.ic_auto_next_chapter),
            commandButton(cmdSpeed, "Rychlost", R.drawable.ic_auto_speed),
            commandButton(cmdSleep, "Časovač spánku", R.drawable.ic_auto_sleep),
        )
    }

    private fun commandButton(cmd: SessionCommand, name: String, iconRes: Int): CommandButton =
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setSessionCommand(cmd)
            .setDisplayName(name)
            .setIconResId(iconRes)
            // SLOT_OVERFLOW = custom akce (±kapitola/rychlost/sleep) NEsmí zabrat sloty BACK/FORWARD
            // hned vedle play/pause — ty patří nativnímu ±10 s seeku (COMMAND_SEEK_BACK/FORWARD).
            // Bez tohoto si první custom buttons (±kapitola) nárokovaly flankující sloty a vytlačily
            // seek dál od play/pause (reklamace user testu v1.29.0). Pořadí od play/pause ven:
            // seek ±10 s (nativní, BACK/FORWARD) → ±kapitola → rychlost → sleep (overflow).
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Plan EVEN — pinneme stabilní audio session id, aby na něj šel deterministicky připojit
        // DRC/normalizér (DynamicsProcessing/LoudnessEnhancer).
        val audioSessionId = C.generateAudioSessionIdV21(this)
        val exo = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            // Android Auto ukáže ±10 s seek tlačítka (BACK/FORWARD sloty hned vedle play/pause)
            // odvozená z těchto inkrementů.
            .setSeekBackIncrementMs(absPrefs.skipSeconds * 1000L)
            .setSeekForwardIncrementMs(absPrefs.skipSeconds * 1000L)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
        // Session id se v media3 nastavuje na instanci (Builder ho nemá) — pinneme ho před přehráním,
        // ať na něj jde deterministicky připojit DRC/normalizér.
        exo.setAudioSessionId(audioSessionId)
        audioBoost = AudioBoost(audioSessionId).also { it.apply(absPrefs.listenDrcLevel, AudioBoost.Profile.LISTEN) }
        ContextCompat.registerReceiver(
            this,
            drcReceiver,
            IntentFilter(ListenNavSignal.ACTION_LISTEN_DRC_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) { startSync(); startMetaTicker() } else { stopSync(); stopMetaTicker(); syncNow() }
                // EVERGREEN — tichá auto-instalace na pozadí nesmí utnout poslech (i se zhasnutou obrazovkou).
                InstallGuard.playbackActive = isPlaying
                notifyListenWidget()
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                notifyListenWidget()
            }
        })
        player = exo
        session = MediaLibrarySession.Builder(this, exo, librarySessionCallback)
            .apply { contentActivityPendingIntent()?.let { setSessionActivity(it) } }
            .build()
    }

    /** PendingIntent na vlastní app (launcher) s extra → po klepnutí na notifikaci se otevře Poslech. */
    private fun contentActivityPendingIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra(ListenNavSignal.EXTRA_OPEN_LISTEN, true)
        } ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** RELAY — Glance se sam neobnovuje: rekni Poslouchej widgetu, at se prekresli. */
    private fun notifyListenWidget() {
        runCatching {
            sendBroadcast(Intent(ListenNavSignal.ACTION_LISTEN_STATE_CHANGED).setPackage(packageName))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    // ---- Android Auto browse strom + resolver + custom akce ----
    // Strom (rootChildren/podcastSection/…) = extension funkce v AudiobookBrowseTree.kt;
    // buildery položek (bookItem/trackItems/…) = AudiobookAutoMedia.kt.

    private val librarySessionCallback = object : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(cmdPrevChapter)
                .add(cmdNextChapter)
                .add(cmdSpeed)
                .add(cmdSleep)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(customLayout)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_PREV_CHAPTER -> prevChapter()
                ACTION_NEXT_CHAPTER -> nextChapter()
                ACTION_SPEED -> cycleSpeed()
                ACTION_SLEEP -> cycleSleep()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Timber.i("[AUTO] onGetLibraryRoot (verze 1.32.0 root sekce)")
            val root = browsableNode(ROOT_ID, "Showlyfin")
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            // Kapitoly aktuální knihy se počítají lokálně (bez síťě).
            if (parentId == NODE_CHAPTERS) {
                val items = chapterItems()
                items.forEach { itemCache[it.mediaId] = it }
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            // CRUISE: ABS větve gateujeme jednotlivě (ne blanket) — custom zdroje (Podcasty) jedou i bez ABS.
            return future {
                val children: List<MediaItem> = when {
                    parentId == ROOT_ID -> rootChildren()
                    parentId == NODE_CONTINUE -> continueItems()
                    parentId == NODE_PODCASTS -> podcastSection()
                    parentId == NODE_LATEST -> latestEpisodes()
                    parentId.startsWith(PREFIX_SRC) -> sourceEpisodes(parentId.removePrefix(PREFIX_SRC))
                    parentId.startsWith(PREFIX_LIB) -> if (repo.isConfigured) libraryBooks(parentId.removePrefix(PREFIX_LIB)) else emptyList()
                    parentId.startsWith(PREFIX_PLIB) -> if (repo.isConfigured) libraryPodcasts(parentId.removePrefix(PREFIX_PLIB)) else emptyList()
                    parentId.startsWith(PREFIX_PODCAST) -> if (repo.isConfigured) podcastEpisodes(parentId.removePrefix(PREFIX_PODCAST)) else emptyList()
                    else -> emptyList()
                }
                Timber.i("[AUTO] children parent='%s' → %d: %s", parentId, children.size,
                    children.joinToString { it.mediaMetadata.title?.toString() ?: it.mediaId })
                children.forEach { itemCache[it.mediaId] = it }
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val cached = itemCache[mediaId]
            return if (cached != null) {
                Futures.immediateFuture(LibraryResult.ofItem(cached, null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val first = mediaItems.firstOrNull()
            // Auto: skok na kapitolu aktuální knihy (`chap:<index>`) — bez nové play session.
            if (mediaItems.size == 1 && first != null && first.mediaId.startsWith(PREFIX_CHAPTER)) {
                val pb = currentPlayback
                val idx = first.mediaId.removePrefix(PREFIX_CHAPTER).toIntOrNull()
                if (pb != null && idx != null) {
                    val bookMs = ((pb.chapters.getOrNull(idx)?.startSec ?: 0.0) * 1000).toLong()
                    val (tIdx, pos) = trackPositionForBookMs(pb, bookMs)
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(trackItems(pb), tIdx, pos),
                    )
                }
            }
            // Auto: jediná položka `abs:<id>` bez URI → expanduj na multi-track playlist + resume.
            if (mediaItems.size == 1 && first != null &&
                first.localConfiguration == null && first.mediaId.startsWith(PREFIX_BOOK)
            ) {
                val itemId = first.mediaId.removePrefix(PREFIX_BOOK)
                return future {
                    val pb = repo.startPlayback(itemId).also { currentPlayback = it }
                    Timber.i("[AUTO] play kniha '%s' tracks=%d", pb.title, pb.tracks.size)
                    val (idx, pos) = trackPositionForBookMs(pb, (pb.startPositionSec * 1000).toLong())
                    MediaSession.MediaItemsWithStartPosition(trackItems(pb), idx, pos)
                }
            }
            // Auto: epizoda `epi:<itemId>|<episodeId>` bez URI → single-track play session + resume.
            if (mediaItems.size == 1 && first != null &&
                first.localConfiguration == null && first.mediaId.startsWith(PREFIX_EPISODE)
            ) {
                val parts = first.mediaId.removePrefix(PREFIX_EPISODE).split("|", limit = 2)
                if (parts.size == 2) {
                    return future {
                        val pb = repo.startEpisodePlayback(parts[0], parts[1]).also { currentPlayback = it }
                        Timber.i("[AUTO] play epizoda '%s' tracks=%d", pb.title, pb.tracks.size)
                        val (idx, pos) = trackPositionForBookMs(pb, (pb.startPositionSec * 1000).toLong())
                        MediaSession.MediaItemsWithStartPosition(trackItems(pb), idx, pos)
                    }
                }
            }
            // CRUISE: direct epizoda custom zdroje (`direct:<sourceId>|<episodeId>`) → načti CELOU frontu
            // epizod zdroje (skip ⏮⏭ na předchozí/další epizodu + auto-navázání), spusť od tapnuté.
            if (mediaItems.size == 1 && first != null &&
                first.localConfiguration == null && first.mediaId.startsWith(PREFIX_DIRECT)
            ) {
                val rest = first.mediaId.removePrefix(PREFIX_DIRECT).split("|", limit = 2)
                if (rest.size == 2) {
                    return future {
                        val items = directSourceEpisodes(rest[0])
                        if (items.isEmpty()) {
                            MediaSession.MediaItemsWithStartPosition(mediaItems, 0, startPositionMs.coerceAtLeast(0L))
                        } else {
                            val idx = items.indexOfFirst { it.mediaId == first.mediaId }.coerceAtLeast(0)
                            // CRUISE: navázat na uloženou pozici (resume sdílený s in-app přehrávačem).
                            val resumeMs = items.getOrNull(idx)?.mediaMetadata?.extras
                                ?.getString(KEY_DIRECT_KEY)?.let { directResume.get(it)?.posMs } ?: 0L
                            MediaSession.MediaItemsWithStartPosition(items.toMutableList(), idx, resumeMs)
                        }
                    }
                }
            }
            // In-app přehrávač posílá už resolvované položky (s URI) → projdou beze změny.
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val first = mediaItems.firstOrNull()
            if (mediaItems.size == 1 && first != null &&
                first.localConfiguration == null && first.mediaId.startsWith(PREFIX_BOOK)
            ) {
                val itemId = first.mediaId.removePrefix(PREFIX_BOOK)
                return future { trackItems(repo.startPlayback(itemId).also { currentPlayback = it }).toMutableList() }
            }
            // CRUISE: direct epizoda custom zdroje → celá fronta epizod zdroje (skip ⏮⏭ + auto-navázání).
            if (mediaItems.size == 1 && first != null &&
                first.localConfiguration == null && first.mediaId.startsWith(PREFIX_DIRECT)
            ) {
                val rest = first.mediaId.removePrefix(PREFIX_DIRECT).split("|", limit = 2)
                if (rest.size == 2) {
                    return future { directSourceEpisodes(rest[0]).ifEmpty { mediaItems }.toMutableList() }
                }
            }
            return Futures.immediateFuture(mediaItems)
        }
    }

    // ---- Custom akce (logika) ----

    private fun nextChapter() {
        val chapters = currentPlayback?.chapters ?: return
        val posSec = bookPosMs() / 1000.0
        val next = chapters.firstOrNull { it.startSec > posSec + 1.0 } ?: return
        seekBookMs((next.startSec * 1000).toLong())
    }

    private fun prevChapter() {
        val chapters = currentPlayback?.chapters ?: return
        val posSec = bookPosMs() / 1000.0
        val current = chapters.lastOrNull { it.startSec <= posSec }
        val target = if (current != null && posSec - current.startSec > 3.0) {
            current.startSec
        } else {
            chapters.lastOrNull { it.startSec < (current?.startSec ?: posSec) - 0.1 }?.startSec ?: 0.0
        }
        seekBookMs((target * 1000).toLong())
    }

    private fun cycleSpeed() {
        val p = player ?: return
        val cur = p.playbackParameters.speed
        val next = SPEEDS.firstOrNull { it > cur + 0.01f } ?: SPEEDS.first()
        p.setPlaybackParameters(p.playbackParameters.withSpeed(next))
    }

    /** Cyklus časovače spánku: vyp → 15 → 30 → 45 → 60 → vyp. */
    private fun cycleSleep() {
        sleepJob?.cancel()
        val nextMinutes = SLEEP_STEPS.firstOrNull { it > sleepMinutes } ?: 0
        sleepMinutes = nextMinutes
        if (nextMinutes <= 0) { sleepJob = null; return }
        sleepJob = scope.launch {
            delay(nextMinutes * 60_000L)
            player?.pause()
            sleepMinutes = 0
            sleepJob = null
        }
    }

    private var sleepMinutes = 0

    /** Pozice v čase celé knihy = offset aktuálního souboru + pozice v něm. */
    private fun bookPosMs(): Long {
        val p = player ?: return 0L
        val offSec = p.currentMediaItem?.mediaMetadata?.extras?.getDouble(KEY_TRACK_OFFSET_SEC) ?: 0.0
        return (offSec * 1000).toLong() + p.currentPosition.coerceAtLeast(0L)
    }

    /** Skok na pozici v čase celé knihy — najde správný soubor a posun v něm. */
    private fun seekBookMs(bookMs: Long) {
        val p = player ?: return
        val target = bookMs.coerceAtLeast(0L)
        val count = p.mediaItemCount
        if (count <= 1) { p.seekTo(target); return }
        var idx = 0
        for (i in 0 until count) {
            val offMs = ((p.getMediaItemAt(i).mediaMetadata.extras
                ?.getDouble(KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
            if (offMs <= target + 1) idx = i else break
        }
        val idxOffMs = ((p.getMediaItemAt(idx).mediaMetadata.extras
            ?.getDouble(KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
        p.seekTo(idx, (target - idxOffMs).coerceAtLeast(0L))
    }

    /** Spustí suspend blok na main scope a vystaví ho jako ListenableFuture (pro Auto callbacky). */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch {
            runCatching { block() }
                .onSuccess { f.set(it) }
                .onFailure { Timber.w(it, "[AUTO] resolve selhal"); f.setException(it) }
        }
        return f
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        syncNow()
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        syncNow()
        InstallGuard.playbackActive = false
        runCatching { unregisterReceiver(drcReceiver) }
        audioBoost?.release()
        audioBoost = null
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (true) {
                delay(absPrefs.syncIntervalSeconds * 1000L)
                syncNow()
            }
        }
    }

    private fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Drží systémovou metadata titulek = PRÁVĚ HRANÁ KAPITOLA (notifikace + Android Auto). Řeší
     * přehrávání spuštěné z Auta (kde [currentPlayback] zná kapitoly); in-app spuštění řeší stejně
     * AudiobookPlayerConnection. Bez toho lišta ukazovala jen (duplikovaný) titul knihy.
     */
    private fun startMetaTicker() {
        if (metaJob?.isActive == true) return
        metaJob = scope.launch {
            while (true) {
                updateChapterMetadata()
                delay(1000)
            }
        }
    }

    private fun stopMetaTicker() {
        metaJob?.cancel()
        metaJob = null
    }

    private fun updateChapterMetadata() {
        val p = player ?: return
        val pb = currentPlayback ?: return
        if (pb.chapters.isEmpty()) return
        val posSec = bookPosMs() / 1000.0
        val chap = pb.chapters.firstOrNull { posSec >= it.startSec && posSec < it.endSec } ?: return
        val cur = p.currentMediaItem ?: return
        if (cur.mediaMetadata.title?.toString() == chap.title) return
        val newMeta = cur.mediaMetadata.buildUpon()
            .setTitle(chap.title)
            .setArtist(pb.title)
            .build()
        runCatching { p.replaceMediaItem(p.currentMediaItemIndex, cur.buildUpon().setMediaMetadata(newMeta).build()) }
    }

    /** Pošle aktuální pozici na ABS (drží „Pokračovat v poslechu"); CRUISE: direct epizody → DirectResumeStore. */
    private fun syncNow() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        // CRUISE: direct epizoda (RSS/YT/NaVýbornou) z Android Auto → ulož pozici do sdíleného resume store
        // (stejný klíč jako in-app → „Pokračovat" v AA + navázání pozice; přehrávání jde přes tentýž player).
        val directKey = extras.getString(KEY_DIRECT_KEY)?.takeIf { it.isNotBlank() }
        if (directKey != null) {
            directResume.save(directKey, p.currentPosition.coerceAtLeast(0L), p.duration.coerceAtLeast(0L))
            return
        }
        val sessionId = extras.getString(KEY_SESSION_ID)?.takeIf { it.isNotBlank() } ?: return  // TUNER: YouTube = bez ABS session
        val durationSec = extras.getDouble(KEY_DURATION_SEC)
        val trackOffsetSec = extras.getDouble(KEY_TRACK_OFFSET_SEC)
        // Pozice v čase CELÉ knihy = offset aktuálního souboru + pozice v něm.
        val posSec = (trackOffsetSec + p.currentPosition / 1000.0).coerceAtLeast(0.0)
        scope.launch {
            repo.syncProgress(sessionId, posSec, absPrefs.syncIntervalSeconds.toDouble(), durationSec)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "abs_session_id"
        const val KEY_DURATION_SEC = "abs_duration_sec"
        const val KEY_TRACK_OFFSET_SEC = "abs_track_offset_sec"
        // Stabilní titul/autor knihy v extras — systémovou metadata titulek přepisujeme na kapitolu,
        // tahle pole drží původní titul knihy i přes reconnect controlleru (cold start za běhu).
        const val KEY_BOOK_TITLE = "abs_book_title"
        const val KEY_BOOK_AUTHOR = "abs_book_author"
        const val ROOT_ID = "root"
        const val NODE_CONTINUE = "node:continue"
        const val NODE_CHAPTERS = "node:chapters"
        const val NODE_PODCASTS = "node:podcasts"
        const val NODE_LATEST = "node:latest"       // CRUISE: „Nejnovější epizody" (uvnitř Podcasty)
        const val PREFIX_LIB = "abslib:"
        const val PREFIX_BOOK = "abs:"
        const val PREFIX_CHAPTER = "chap:"
        const val PREFIX_PLIB = "podlib:"          // ABS podcast knihovna
        const val PREFIX_PODCAST = "pod:"           // ABS podcast (browsable → epizody)
        const val PREFIX_EPISODE = "epi:"           // ABS epizoda (playable, `epi:<itemId>|<episodeId>`)
        const val PREFIX_SRC = "src:"               // CRUISE: custom zdroj (browsable → epizody)
        const val PREFIX_DIRECT = "direct:"         // CRUISE: direct epizoda (`direct:<sourceId>|<episodeId>`)
        const val KEY_DIRECT_KEY = "direct_resume_key"   // CRUISE: resume klíč direct epizody (`rss:`/`yt:`)
        const val ACTION_PREV_CHAPTER = "com.github.jankoran90.showlyfin.PREV_CHAPTER"
        const val ACTION_NEXT_CHAPTER = "com.github.jankoran90.showlyfin.NEXT_CHAPTER"
        const val ACTION_SPEED = "com.github.jankoran90.showlyfin.SPEED"
        const val ACTION_SLEEP = "com.github.jankoran90.showlyfin.SLEEP"
        private val SPEEDS = floatArrayOf(1.0f, 1.2f, 1.5f, 1.7f, 2.0f, 0.8f)
        private val SLEEP_STEPS = intArrayOf(15, 30, 45, 60)
        // Internal = čtou je browse extension funkce v AudiobookBrowseTree.kt.
        internal const val CONTINUE_LIMIT = 25
        internal const val LATEST_LIMIT = 40   // CRUISE: strop „Nejnovější epizody" napříč zdroji
    }
}
