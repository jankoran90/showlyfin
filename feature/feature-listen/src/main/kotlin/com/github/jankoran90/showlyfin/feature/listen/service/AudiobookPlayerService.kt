package com.github.jankoran90.showlyfin.feature.listen.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.feature.listen.R
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
 */
@AndroidEntryPoint
class AudiobookPlayerService : MediaLibraryService() {

    @Inject lateinit var repo: AbsRepository

    private var session: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var sleepJob: Job? = null

    /** Cache položek vrácených v onGetChildren (pro onGetItem). Přístup jen z main threadu. */
    private val itemCache = HashMap<String, MediaItem>()

    /** Právě otevřená kniha (pro kapitoly v Auto + ±kapitola tlačítka). Jen main thread. */
    private var currentPlayback: AbsPlayback? = null

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
        val exo = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            // Android Auto ukáže ±10 s seek tlačítka (BACK/FORWARD sloty hned vedle play/pause)
            // odvozená z těchto inkrementů.
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startSync() else { stopSync(); syncNow() }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    // ---- Android Auto browse strom + resolver + custom akce ----

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
            if (!repo.isConfigured) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            // Kapitoly aktuální knihy se počítají lokálně (bez síťě).
            if (parentId == NODE_CHAPTERS) {
                val items = chapterItems()
                items.forEach { itemCache[it.mediaId] = it }
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future {
                val children: List<MediaItem> = when {
                    parentId == ROOT_ID -> rootChildren()
                    parentId == NODE_CONTINUE -> continueBooks()
                    parentId.startsWith(PREFIX_LIB) -> libraryBooks(parentId.removePrefix(PREFIX_LIB))
                    else -> emptyList()
                }
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
                    val (idx, pos) = trackPositionForBookMs(pb, (pb.startPositionSec * 1000).toLong())
                    MediaSession.MediaItemsWithStartPosition(trackItems(pb), idx, pos)
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

    // ---- Strom: uzly ----

    /** Root → „Pokračovat" + (pokud hraje kniha) „Kapitoly" + jednotlivé ABS knihovny. */
    private suspend fun rootChildren(): List<MediaItem> {
        val nodes = mutableListOf(browsableNode(NODE_CONTINUE, "Pokračovat"))
        if (currentPlayback != null) nodes += browsableNode(NODE_CHAPTERS, "Kapitoly")
        repo.getAudiobookLibraries().forEach { nodes += browsableNode("$PREFIX_LIB${it.id}", it.name) }
        return nodes
    }

    /** Rozposlouchané a nedokončené knihy napříč knihovnami. */
    private suspend fun continueBooks(): List<MediaItem> =
        repo.getAudiobookLibraries()
            .flatMap { repo.getAudiobooks(it.id) }
            .filter { it.progress > 0.0 && !it.isFinished }
            .sortedByDescending { it.currentTimeSec }
            .take(CONTINUE_LIMIT)
            .map(::bookItem)

    private suspend fun libraryBooks(libraryId: String): List<MediaItem> =
        repo.getAudiobooks(libraryId).map(::bookItem)

    /** Kapitoly aktuální knihy jako playable položky (`chap:<index>`) — klik skočí na kapitolu. */
    private fun chapterItems(): List<MediaItem> {
        val pb = currentPlayback ?: return emptyList()
        val artwork = pb.coverUrl?.let(Uri::parse)
        return pb.chapters.mapIndexed { i, ch ->
            MediaItem.Builder()
                .setMediaId("$PREFIX_CHAPTER$i")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(ch.title)
                        .setArtist(pb.title)
                        .setArtworkUri(artwork)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                        .build(),
                )
                .build()
        }
    }

    // ---- MediaItem builders ----

    /** Browsable (neplayable) uzel pro Android Auto menu. */
    private fun browsableNode(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                    .build(),
            )
            .build()

    /** Playable položka knihy (mediaId `abs:<itemId>`) — resolver ji expanduje na tracky. */
    private fun bookItem(b: Audiobook): MediaItem =
        MediaItem.Builder()
            .setMediaId("$PREFIX_BOOK${b.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(b.title)
                    .setArtist(b.author)
                    .setArtworkUri(b.coverUrl?.let(Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .build(),
            )
            .build()

    /** Multi-track playlist z play session (shodné s AudiobookPlayerConnection). */
    private fun trackItems(pb: AbsPlayback): List<MediaItem> {
        val artwork = pb.coverUrl?.let(Uri::parse)
        return pb.tracks.map { t ->
            val extras = Bundle().apply {
                putString(KEY_SESSION_ID, pb.sessionId)
                putDouble(KEY_DURATION_SEC, pb.durationSec)
                putDouble(KEY_TRACK_OFFSET_SEC, t.startOffsetSec)
            }
            MediaItem.Builder()
                .setUri(t.url)
                .setMediaId("${pb.sessionId}_${t.index}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(pb.title)
                        .setArtist(pb.author)
                        .setArtworkUri(artwork)
                        .setExtras(extras)
                        .build(),
                )
                .build()
        }
    }

    /** Čas celé knihy [ms] → (index tracku, pozice v tracku ms). */
    private fun trackPositionForBookMs(pb: AbsPlayback, bookMs: Long): Pair<Int, Long> {
        val target = bookMs.coerceAtLeast(0L)
        if (pb.tracks.size <= 1) return 0 to target
        var idx = 0
        for (i in pb.tracks.indices) {
            val offMs = (pb.tracks[i].startOffsetSec * 1000).toLong()
            if (offMs <= target + 1) idx = i else break
        }
        val idxOffMs = (pb.tracks[idx].startOffsetSec * 1000).toLong()
        return idx to (target - idxOffMs).coerceAtLeast(0L)
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
                delay(SYNC_INTERVAL_MS)
                syncNow()
            }
        }
    }

    private fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /** Pošle aktuální pozici na ABS (drží „Pokračovat v poslechu"). */
    private fun syncNow() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val sessionId = extras.getString(KEY_SESSION_ID) ?: return
        val durationSec = extras.getDouble(KEY_DURATION_SEC)
        val trackOffsetSec = extras.getDouble(KEY_TRACK_OFFSET_SEC)
        // Pozice v čase CELÉ knihy = offset aktuálního souboru + pozice v něm.
        val posSec = (trackOffsetSec + p.currentPosition / 1000.0).coerceAtLeast(0.0)
        scope.launch {
            repo.syncProgress(sessionId, posSec, SYNC_INTERVAL_MS / 1000.0, durationSec)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "abs_session_id"
        const val KEY_DURATION_SEC = "abs_duration_sec"
        const val KEY_TRACK_OFFSET_SEC = "abs_track_offset_sec"
        const val ROOT_ID = "root"
        const val NODE_CONTINUE = "node:continue"
        const val NODE_CHAPTERS = "node:chapters"
        const val PREFIX_LIB = "abslib:"
        const val PREFIX_BOOK = "abs:"
        const val PREFIX_CHAPTER = "chap:"
        const val ACTION_PREV_CHAPTER = "com.github.jankoran90.showlyfin.PREV_CHAPTER"
        const val ACTION_NEXT_CHAPTER = "com.github.jankoran90.showlyfin.NEXT_CHAPTER"
        const val ACTION_SPEED = "com.github.jankoran90.showlyfin.SPEED"
        const val ACTION_SLEEP = "com.github.jankoran90.showlyfin.SLEEP"
        private val SPEEDS = floatArrayOf(1.0f, 1.2f, 1.5f, 1.7f, 2.0f, 0.8f)
        private val SLEEP_STEPS = intArrayOf(15, 30, 45, 60)
        private const val CONTINUE_LIMIT = 25
        private const val SYNC_INTERVAL_MS = 15_000L
        private const val SEEK_INCREMENT_MS = 10_000L
    }
}
