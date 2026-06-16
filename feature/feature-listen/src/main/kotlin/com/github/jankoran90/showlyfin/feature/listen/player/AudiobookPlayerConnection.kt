package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.abs.model.Chapter
import com.github.jankoran90.showlyfin.feature.listen.service.AudiobookPlayerService
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Most mezi UI a [AudiobookPlayerService]. Drží jeden [MediaController] (sdílený fullscreen
 * playerem i mini-playerem), publikuje [PlayerState] (poll 500 ms + Player listener) a
 * forwarduje příkazy. Kapitoly aktuální knihy drží zvlášť pro skip ◀▶ a název kapitoly.
 * Pro podcasty drží frontu epizod (auto-advance + auto mark-finished na konci).
 */
@Singleton
class AudiobookPlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repo: AbsRepository,
    private val prefs: AbsPreferences,
    private val downloads: EpisodeDownloadManager,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null
    private var pollJob: Job? = null
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters = _chapters.asStateFlow()

    /** Fronta podcastových epizod. */
    private val _queue = MutableStateFlow<List<QueuedEpisode>>(loadPersistedQueue())
    val queue = _queue.asStateFlow()

    /** Aktuálně hraná podcast epizoda (null = audiokniha → bez fronty/auto-mark). */
    private var currentEpisode: QueuedEpisode? = null
    private var advancing = false

    // Stabilní titul/autor knihy — drží se mimo MediaItem metadata, protože systémovou metadata titulek
    // (notifikace + Android Auto) přepisujeme na PRÁVĚ HRANOU KAPITOLU. In-app UI tak ukáže titul knihy
    // velkým písmem + kapitolu zvlášť, kdežto lišta systému ukáže kapitolu (dřív se duplikoval titul).
    private var bookTitle: String = ""
    private var bookAuthor: String? = null

    /** Sleep „do konce kapitoly/epizody": cílový čas kapitoly (book ms) nebo -1 = do konce epizody. */
    private var sleepEndChapterMs: Long? = null
    private var sleepEndOfEpisode = false

    private fun loadPersistedQueue(): List<QueuedEpisode> {
        if (!prefs.persistQueue) return emptyList()
        val json = prefs.queueJson.ifBlank { return emptyList() }
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                QueuedEpisode(
                    itemId = o.getString("itemId"),
                    episodeId = o.getString("episodeId"),
                    title = o.optString("title"),
                    coverUrl = if (o.isNull("coverUrl")) null else o.optString("coverUrl").takeIf { it.isNotBlank() },
                    guest = if (o.isNull("guest")) null else o.optString("guest").takeIf { it.isNotBlank() },
                    description = if (o.isNull("description")) null else o.optString("description").takeIf { it.isNotBlank() },
                    podcastTitle = if (o.isNull("podcastTitle")) null else o.optString("podcastTitle").takeIf { it.isNotBlank() },
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun persistQueue() {
        if (!prefs.persistQueue) { prefs.queueJson = ""; return }
        val arr = JSONArray()
        _queue.value.forEach { q ->
            arr.put(
                JSONObject()
                    .put("itemId", q.itemId)
                    .put("episodeId", q.episodeId)
                    .put("title", q.title)
                    .put("coverUrl", q.coverUrl ?: JSONObject.NULL)
                    .put("guest", q.guest ?: JSONObject.NULL)
                    .put("description", q.description ?: JSONObject.NULL)
                    .put("podcastTitle", q.podcastTitle ?: JSONObject.NULL),
            )
        }
        prefs.queueJson = arr.toString()
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState()
            if (player.playbackState == Player.STATE_ENDED) onPlaybackEnded()
        }
    }

    private fun ensureController() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, AudiobookPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(playerListener)
            startPolling()
            pending?.let { it(c); pending = null }
            pushState()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending = block; ensureController() }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                pushState()
                delay(500)
            }
        }
    }

    private fun pushState() {
        val c = controller ?: return
        val pos = bookPosMs(c)
        val posSec = pos / 1000.0
        val currentChapter = _chapters.value.firstOrNull { posSec >= it.startSec && posSec < it.endSec }
        val extras = c.currentMediaItem?.mediaMetadata?.extras
        val bookDurationMs = (extras?.getDouble(AudiobookPlayerService.KEY_DURATION_SEC) ?: 0.0)
            .let { (it * 1000).toLong() }
        // Stabilní titul/autor knihy: z polí (in-app start) nebo z extras (reconnect / Auto start). Systémová
        // metadata.title je totiž přepsaná na kapitolu, takže ji pro in-app titul číst nelze.
        val bookTitleNow = bookTitle.ifBlank { extras?.getString(AudiobookPlayerService.KEY_BOOK_TITLE).orEmpty() }
        val bookAuthorNow = bookAuthor ?: extras?.getString(AudiobookPlayerService.KEY_BOOK_AUTHOR)

        // Systémová metadata (notifikace + Android Auto): u audioknihy s kapitolami ukaž PRÁVĚ HRANOU
        // KAPITOLU jako titulek (autor = kniha). Bez toho lišta jen duplikovala titul knihy. Podcast
        // (epizoda, bez kapitol) necháváme — titulek = název epizody. replaceMediaItem mění jen metadata
        // (stejné URI) → bez přerušení přehrávání. (Auto-spuštěné přehrávání řeší stejně i service.)
        if (currentEpisode == null && currentChapter != null) {
            val cur = c.currentMediaItem
            if (cur != null && cur.mediaMetadata.title?.toString() != currentChapter.title) {
                val newMeta = cur.mediaMetadata.buildUpon()
                    .setTitle(currentChapter.title)
                    .setArtist(bookTitleNow.ifBlank { bookAuthorNow ?: "" })
                    .build()
                runCatching { c.replaceMediaItem(c.currentMediaItemIndex, cur.buildUpon().setMediaMetadata(newMeta).build()) }
            }
        }

        // Sleep „do konce kapitoly" — pauzni, jakmile pozice překročí konec cílové kapitoly.
        sleepEndChapterMs?.let { target ->
            if (pos >= target && c.isPlaying) {
                c.pause()
                sleepEndChapterMs = null
                _state.update { it.copy(sleepAtEnd = false) }
            }
        }

        _state.update {
            it.copy(
                isActive = c.mediaItemCount > 0,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                title = bookTitleNow.ifBlank { c.mediaMetadata.title?.toString() ?: it.title },
                author = bookAuthorNow ?: c.mediaMetadata.artist?.toString() ?: it.author,
                coverUrl = c.mediaMetadata.artworkUri?.toString() ?: it.coverUrl,
                positionMs = pos,
                durationMs = bookDurationMs.takeIf { d -> d > 0 } ?: it.durationMs,
                speed = c.playbackParameters.speed,
                currentChapterTitle = currentChapter?.title,
                currentChapterIndex = currentChapter?.index,
                skipSeconds = prefs.skipSeconds,
                showRemainingTime = prefs.showRemainingTime,
                showSpeedButton = prefs.showSpeedButton,
                showSleepButton = prefs.showSleepButton,
                queueSwipeAction = prefs.queueSwipeAction,
            )
        }
    }

    fun playBook(
        pb: AbsPlayback,
        fromStart: Boolean,
        startOverrideSec: Double? = null,
        episode: QueuedEpisode? = null,
        itemId: String? = null,
    ) {
        currentEpisode = episode
        // Podcast epizoda zůstává ve frontě (highlight „hraje") — manuální přeskok ji NEodebírá,
        // odejde až přirozeným dohráním. Když ve frontě ještě není (spuštěná z detailu), přidej ji nahoru.
        if (episode != null && _queue.value.none { it.episodeId == episode.episodeId }) {
            setQueue(listOf(episode) + _queue.value)
        }
        _chapters.value = pb.chapters
        bookTitle = pb.title
        bookAuthor = pb.author
        _state.update {
            it.copy(
                isActive = true, title = pb.title, author = pb.author, coverUrl = pb.coverUrl,
                guest = episode?.guest,
                durationMs = (pb.durationSec * 1000).toLong(),
                isPodcastEpisode = episode != null,
                currentItemId = itemId ?: episode?.itemId,
                currentEpisodeId = episode?.episodeId,
            )
        }
        withController { c ->
            // Persist pozici ODCHÁZEJÍCÍ položky PŘED přepnutím média — jinak rychlé přepínání
            // (klik tam/zpět) ztratí progres, protože periodický sync (15 s) ani pauza neproběhnou.
            val outExtras = c.currentMediaItem?.mediaMetadata?.extras
            val outSession = outExtras?.getString(AudiobookPlayerService.KEY_SESSION_ID)
            if (!outSession.isNullOrBlank() && outSession != pb.sessionId) {
                val outDur = outExtras.getDouble(AudiobookPlayerService.KEY_DURATION_SEC)
                val outPos = bookPosMs(c) / 1000.0
                scope.launch { repo.syncProgress(outSession, outPos, 0.0, outDur) }
            }
            val artwork = pb.coverUrl?.let(Uri::parse)
            val items = pb.tracks.map { t ->
                val extras = Bundle().apply {
                    putString(AudiobookPlayerService.KEY_SESSION_ID, pb.sessionId)
                    putDouble(AudiobookPlayerService.KEY_DURATION_SEC, pb.durationSec)
                    putDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC, t.startOffsetSec)
                    putString(AudiobookPlayerService.KEY_BOOK_TITLE, pb.title)
                    pb.author?.let { putString(AudiobookPlayerService.KEY_BOOK_AUTHOR, it) }
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
            c.setMediaItems(items)
            c.prepare()
            // Rychlost: zapamatovaná (zvlášť kniha/podcast) nebo výchozí z nastavení.
            val targetSpeed = if (prefs.rememberSpeed) {
                if (episode != null) prefs.lastPodcastSpeed else prefs.lastBookSpeed
            } else prefs.defaultSpeed
            c.setPlaybackSpeed(targetSpeed.coerceIn(0.5f, 3.5f))
            val startBookMs = when {
                startOverrideSec != null -> (startOverrideSec * 1000).toLong()
                fromStart -> 0L
                else -> (pb.startPositionSec * 1000).toLong()
            }
            if (startBookMs > 0) seekBook(c, startBookMs)
            c.playWhenReady = true
        }
    }

    /**
     * TUNER (SHW-62): přehraj přímou audio URL (YouTube podcast) přes stejný přehrávač jako ABS
     * podcasty — mini-player, notifikace, zámek, běh na pozadí, rychlost, sleep. BEZ ABS session
     * (KEY_SESSION_ID prázdný → service neskáče sync na ABS; žádný auto-mark/fronta).
     * [mediaId] např. "yt:<videoId>" (unikátní). [startMs] = resume pozice (zatím 0).
     */
    fun playDirect(
        url: String,
        title: String,
        author: String?,
        coverUrl: String?,
        durationSec: Double,
        mediaId: String,
        startMs: Long = 0L,
    ) {
        currentEpisode = null          // ne ABS epizoda → onPlaybackEnded() nic neudělá
        advancing = false
        _chapters.value = emptyList()
        bookTitle = title
        bookAuthor = author
        _state.update {
            it.copy(
                isActive = true, title = title, author = author, coverUrl = coverUrl, guest = null,
                durationMs = (durationSec * 1000).toLong(),
                isPodcastEpisode = true, currentItemId = null, currentEpisodeId = null,
            )
        }
        withController { c ->
            // Persist pozici ODCHÁZEJÍCÍ ABS položky (přepnutí z knihy/epizody na YouTube).
            val outExtras = c.currentMediaItem?.mediaMetadata?.extras
            val outSession = outExtras?.getString(AudiobookPlayerService.KEY_SESSION_ID)
            if (!outSession.isNullOrBlank()) {
                val outDur = outExtras.getDouble(AudiobookPlayerService.KEY_DURATION_SEC)
                val outPos = bookPosMs(c) / 1000.0
                scope.launch { repo.syncProgress(outSession, outPos, 0.0, outDur) }
            }
            val artwork = coverUrl?.let(Uri::parse)
            val extras = Bundle().apply {
                // KEY_SESSION_ID schválně NEnastaveno → service.syncNow() se přeskočí (žádný ABS sync).
                putDouble(AudiobookPlayerService.KEY_DURATION_SEC, durationSec)
                putDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC, 0.0)
                putString(AudiobookPlayerService.KEY_BOOK_TITLE, title)
                author?.let { putString(AudiobookPlayerService.KEY_BOOK_AUTHOR, it) }
            }
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(author)
                        .setArtworkUri(artwork)
                        .setExtras(extras)
                        .build(),
                )
                .build()
            c.setMediaItems(listOf(item))
            c.prepare()
            val targetSpeed = if (prefs.rememberSpeed) prefs.lastPodcastSpeed else prefs.defaultSpeed
            c.setPlaybackSpeed(targetSpeed.coerceIn(0.5f, 3.5f))
            if (startMs > 0) c.seekTo(startMs)
            c.playWhenReady = true
        }
    }

    /** Pozice v čase CELÉ knihy = offset aktuálního souboru + pozice v něm. */
    private fun bookPosMs(c: MediaController): Long {
        val offSec = c.currentMediaItem?.mediaMetadata?.extras
            ?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0
        return (offSec * 1000).toLong() + c.currentPosition.coerceAtLeast(0L)
    }

    /** Skok na pozici v čase celé knihy — najde správný soubor a posun v něm. */
    private fun seekBook(c: MediaController, bookMs: Long) {
        val target = bookMs.coerceAtLeast(0L)
        val count = c.mediaItemCount
        if (count <= 1) { c.seekTo(target); return }
        var idx = 0
        for (i in 0 until count) {
            val offMs = ((c.getMediaItemAt(i).mediaMetadata.extras
                ?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
            if (offMs <= target + 1) idx = i else break
        }
        val idxOffMs = ((c.getMediaItemAt(idx).mediaMetadata.extras
            ?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
        c.seekTo(idx, (target - idxOffMs).coerceAtLeast(0L))
    }

    fun playPause() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    /** [ms] je v čase celé knihy. */
    fun seekTo(ms: Long) = withController { seekBook(it, ms) }

    fun seekBy(deltaMs: Long) = withController { c -> seekBook(c, bookPosMs(c) + deltaMs) }

    fun nextChapter() = withController { c ->
        val posSec = bookPosMs(c) / 1000.0
        val next = _chapters.value.firstOrNull { it.startSec > posSec + 1.0 }
        if (next != null) seekBook(c, (next.startSec * 1000).toLong())
    }

    fun prevChapter() = withController { c ->
        val posSec = bookPosMs(c) / 1000.0
        // do začátku aktuální kapitoly; pokud jsme < 3 s v ní, skoč na předchozí
        val current = _chapters.value.lastOrNull { it.startSec <= posSec }
        val target = if (current != null && posSec - current.startSec > 3.0) {
            current.startSec
        } else {
            _chapters.value.lastOrNull { it.startSec < (current?.startSec ?: posSec) - 0.1 }?.startSec ?: 0.0
        }
        seekBook(c, (target * 1000).toLong())
    }

    /** Skok na konkrétní kapitolu (čas celé knihy). Pro seznam kapitol v playeru. */
    fun seekToChapter(startSec: Double) = withController { seekBook(it, (startSec * 1000).toLong()) }

    fun setSpeed(speed: Float) = withController { c ->
        val s = speed.coerceIn(0.5f, 3.5f)
        c.setPlaybackSpeed(s)
        if (prefs.rememberSpeed) {
            if (_state.value.isPodcastEpisode) prefs.lastPodcastSpeed = s else prefs.lastBookSpeed = s
        }
    }

    /** Sleep timer v minutách; null = zrušit. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        // minutový časovač přebíjí „do konce kapitoly/epizody"
        sleepEndChapterMs = null
        sleepEndOfEpisode = false
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepMinutesLeft = null, sleepAtEnd = false) }
            return
        }
        _state.update { it.copy(sleepAtEnd = false) }
        sleepJob = scope.launch {
            var left = minutes
            while (left > 0) {
                _state.update { it.copy(sleepMinutesLeft = left) }
                delay(60_000)
                left--
            }
            _state.update { it.copy(sleepMinutesLeft = null) }
            controller?.pause()
        }
    }

    /** Sleep „do konce kapitoly" (audiokniha) nebo „do konce epizody" (podcast). */
    fun setSleepEndOfCurrent() = withController { c ->
        sleepJob?.cancel(); sleepJob = null
        _state.update { it.copy(sleepMinutesLeft = null) }
        if (_state.value.isPodcastEpisode) {
            sleepEndChapterMs = null
            sleepEndOfEpisode = true
        } else {
            val posSec = bookPosMs(c) / 1000.0
            val chapterEndSec = _chapters.value.firstOrNull { posSec >= it.startSec && posSec < it.endSec }?.endSec
            sleepEndChapterMs = ((chapterEndSec ?: (state.value.durationMs / 1000.0)) * 1000).toLong()
            sleepEndOfEpisode = false
        }
        _state.update { it.copy(sleepAtEnd = true) }
    }

    // ──────────────────────────── Fronta epizod ────────────────────────────

    private fun setQueue(list: List<QueuedEpisode>) {
        _queue.value = list
        persistQueue()
    }

    private fun currentIndex(): Int =
        _queue.value.indexOfFirst { it.episodeId == currentEpisode?.episodeId }

    /** Přidá epizodu do fronty. [atFront] = hned ZA aktuálně hranou, jinak na konec. Bez duplicit. */
    fun enqueue(episode: QueuedEpisode, atFront: Boolean) {
        val without = _queue.value.filterNot { it.episodeId == episode.episodeId }
        if (!atFront) { setQueue(without + episode); return }
        val curIdx = without.indexOfFirst { it.episodeId == currentEpisode?.episodeId }
        setQueue(
            if (curIdx >= 0) without.toMutableList().apply { add(curIdx + 1, episode) }
            else listOf(episode) + without,
        )
    }

    fun removeFromQueue(episodeId: String) {
        setQueue(_queue.value.filterNot { it.episodeId == episodeId })
    }

    fun clearQueue() = setQueue(emptyList())

    /** Vymaže CELOU frontu včetně aktuálně přehrávaného → přehrávač zůstane prázdný (neaktivní). */
    fun clearAll() {
        withController { c ->
            c.stop()
            c.clearMediaItems()
        }
        currentEpisode = null
        _chapters.value = emptyList()
        setQueue(emptyList())
        _state.value = PlayerState()
    }

    /** Přesun položky ve frontě (drag reorder). */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val q = _queue.value.toMutableList()
        if (fromIndex !in q.indices || toIndex !in q.indices || fromIndex == toIndex) return
        q.add(toIndex, q.removeAt(fromIndex))
        setQueue(q)
    }

    /** Přesun epizody na začátek fronty (swipe akce). */
    fun moveToFront(episodeId: String) {
        val q = _queue.value
        val idx = q.indexOfFirst { it.episodeId == episodeId }
        if (idx > 0) moveQueueItem(idx, 0)
    }

    /** Přehraj konkrétní epizodu z fronty hned (klik v seznamu). NEodebírá ji z fronty. */
    fun playQueued(episode: QueuedEpisode) {
        scope.launch {
            runCatching { repo.startEpisodePlayback(episode.itemId, episode.episodeId) }
                .onSuccess { pb -> playBook(pb, fromStart = false, episode = episode, itemId = episode.itemId) }
                .onFailure { Timber.w(it, "[Listen] přehrání epizody z fronty selhalo") }
        }
    }

    /** Přeskoč na další epizodu ve frontě za aktuální (skip ▶). No-op když žádná není. */
    fun playNextInQueue() {
        val q = _queue.value
        val idx = currentIndex()
        val next = if (idx >= 0) q.getOrNull(idx + 1) else q.firstOrNull()
        if (next != null) playQueued(next)
    }

    /** Přeskoč na předchozí epizodu ve frontě (skip ◀). Když žádná není, skoč na začátek aktuální. */
    fun playPrevInQueue() {
        val q = _queue.value
        val idx = currentIndex()
        val prev = if (idx > 0) q.getOrNull(idx - 1) else null
        if (prev != null) playQueued(prev) else withController { seekBook(it, 0) }
    }

    /**
     * Konec přehrávání podcast epizody. Dle nastavení: označ dokončenou, smaž stažení, přehraj
     * další z fronty / pokračuj dalšími epizodami podcastu. U audioknihy ([currentEpisode] == null)
     * neděláme nic.
     */
    private fun onPlaybackEnded() {
        if (advancing) return
        val ended = currentEpisode ?: return
        advancing = true
        currentEpisode = null

        if (prefs.autoMarkFinished) {
            scope.launch { repo.setEpisodeFinished(ended.itemId, ended.episodeId, true) }
        }
        if (prefs.deleteDownloadAfterFinish && downloads.localFile(ended.episodeId) != null) {
            downloads.delete(ended.episodeId)
        }

        // Sleep „do konce epizody" → zastav, nepokračuj.
        if (sleepEndOfEpisode) {
            sleepEndOfEpisode = false
            _state.update { it.copy(sleepAtEnd = false) }
            advancing = false
            return
        }
        // Dohraná epizoda přirozeně odejde z fronty; další = ta, co byla hned za ní.
        val q = _queue.value
        val endedIdx = q.indexOfFirst { it.episodeId == ended.episodeId }
        val next = if (endedIdx >= 0) q.getOrNull(endedIdx + 1) else q.firstOrNull()
        setQueue(q.filterNot { it.episodeId == ended.episodeId })

        if (!prefs.autoAdvanceQueue) { advancing = false; return }

        if (next != null) {
            scope.launch {
                runCatching { repo.startEpisodePlayback(next.itemId, next.episodeId) }
                    .onSuccess { pb -> playBook(pb, fromStart = false, episode = next, itemId = next.itemId) }
                    .onFailure { Timber.w(it, "[Listen] auto-advance fronty selhal") }
                advancing = false
            }
            return
        }

        // Fronta prázdná → volitelně pokračuj další nepřehranou epizodou téhož podcastu.
        if (prefs.continuePodcastAfterQueue) {
            scope.launch {
                val detail = runCatching { repo.getPodcastDetail(ended.itemId) }.getOrNull()
                val nextEp = detail?.episodes?.firstOrNull { !it.isFinished && it.id != ended.episodeId }
                if (nextEp != null) {
                    val q = QueuedEpisode(nextEp.itemId, nextEp.id, nextEp.title, ended.coverUrl, nextEp.guest, nextEp.description, detail.podcast.title)
                    runCatching { repo.startEpisodePlayback(nextEp.itemId, nextEp.id) }
                        .onSuccess { pb -> playBook(pb, fromStart = false, episode = q, itemId = nextEp.itemId) }
                        .onFailure { Timber.w(it, "[Listen] pokračování podcastu selhalo") }
                }
                advancing = false
            }
            return
        }
        advancing = false
    }
}
