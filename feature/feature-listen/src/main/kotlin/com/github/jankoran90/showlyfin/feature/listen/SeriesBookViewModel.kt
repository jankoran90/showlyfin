package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRule
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRuleStore
import com.github.jankoran90.showlyfin.feature.listen.player.choosePlaybackResume
import com.github.jankoran90.showlyfin.feature.listen.player.ejectIfLoaded
import com.github.jankoran90.showlyfin.feature.listen.player.setQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * Jedna kapitola knihy ze série = jeden díl pořadu (RSS / YouTube / ČT). Obrazovka pořadu ji postaví
 * ze svého typu dílu: [queued] = jak díl přehrát (stejně jako z fronty), [download] = jak ho stáhnout
 * (null = zdroj stažení neumí), [onVideo] = přehrát video (null = jen poslech, třeba dětský profil).
 */
data class SeriesBookChapter(
    val key: String,
    val title: String,
    val durationSec: Double,
    val date: String?,
    val image: String?,
    val description: String?,
    val queued: QueuedEpisode,
    val download: OfflineRequest?,
    val onVideo: (() -> Unit)? = null,
)

/** Stav kapitoly pro UI (pozice z audio i video úložiště, stejné pravidlo jako seznam dílů). */
data class ChapterProgress(val posMs: Long, val durMs: Long, val isFinished: Boolean)

/**
 * Slovo 2026-10-13 (user „Po jedné stopě, Trabantem, Ďábelské káry dát do struktury jako audioknihy —
 * kapitoly, každá série jedna karta") — přehrávání a stahování série jako knihy. Přehrát = kapitola
 * + zbytek knihy hned za ní ve frontě (fronta pak sama navazuje), ostatní položky fronty zůstanou za knihou.
 */
@HiltViewModel
class SeriesBookViewModel @Inject constructor(
    private val connection: AudiobookPlayerConnection,
    private val offline: OfflineDownloadManager,
    private val resumeStore: DirectResumeStore,
    private val videoResumeStore: VideoResumeStore,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
    private val ruleStore: SeriesRuleStore,
) : ViewModel() {

    /** Ruční knihy (pravidla „díly obsahující text") — změna = přeskupení pořadu. */
    val seriesRules = ruleStore.rules

    fun ruleTitle(sourceKey: String, episodeTitle: String): String? = ruleStore.titleFor(sourceKey, episodeTitle)

    fun ruleFor(sourceKey: String, title: String): SeriesRule? =
        seriesRules.value[sourceKey]?.firstOrNull { it.title.equals(title, ignoreCase = true) }

    fun saveRule(sourceKey: String, original: String?, rule: SeriesRule) {
        if (original != null && !original.equals(rule.title, ignoreCase = true)) ruleStore.remove(sourceKey, original)
        ruleStore.put(sourceKey, rule)
    }

    fun removeRule(sourceKey: String, title: String) = ruleStore.remove(sourceKey, title)

    val playerState = connection.state
    val resumeMarks = resumeStore.marks
    val videoResumeMarks = videoResumeStore.marks
    val offlineStates = offline.states

    val layout: StateFlow<HomeLayout> = HomeLayoutPrefs.observe(prefs)
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayoutPrefs.read(prefs))

    fun progressOf(key: String): ChapterProgress? {
        val a = resumeMarks.value[key]
        val v = videoResumeMarks.value[key]
        return choosePlaybackResume(a?.posMs, a?.durMs, a?.isFinished == true, v?.posMs, v?.durMs, a?.updatedAt ?: 0L, v?.updatedAt ?: 0L)
            ?.let { ChapterProgress(it.posMs, it.durMs, it.isFinished) }
    }

    /**
     * Kde knihu „Pokračovat": kapitola právě v přehrávači → poslední kapitola s pozicí (rozposlouchaná
     * = ona, dohraná = další po ní) → první. Index do [chapters].
     */
    fun continueIndex(chapters: List<SeriesBookChapter>): Int {
        val current = playerState.value.currentEpisodeId
        chapters.indexOfFirst { it.key == current }.takeIf { it >= 0 && playerState.value.isActive }?.let { return it }
        val last = chapters.indexOfLast { progressOf(it.key) != null }
        if (last < 0) return 0
        return if (progressOf(chapters[last].key)?.isFinished == true) (last + 1).coerceAtMost(chapters.lastIndex) else last
    }

    /** Přehraj kapitolu [index] a zbytek knihy zařaď hned za ni (bez duplicit, cizí fronta zůstane za knihou). */
    fun playFrom(chapters: List<SeriesBookChapter>, index: Int) {
        val ch = chapters.getOrNull(index) ?: return
        // Zbytek knihy za kapitolu i když už je načtená (spuštěná třeba z Domů) — jinak by po ní fronta nenavázala.
        val bookKeys = chapters.mapTo(HashSet()) { it.key }
        val tail = chapters.drop(index).map { it.queued }
        connection.setQueue(tail + connection.queue.value.filterNot { it.episodeId in bookKeys })
        val st = playerState.value
        if (st.isActive && st.currentEpisodeId == ch.key) {
            if (!st.isPlaying) connection.play()
            return
        }
        connection.playDirectEpisode(ch.queued)
    }

    /** „Od začátku": smaž pozice všech kapitol a hraj první. */
    fun playFromStart(chapters: List<SeriesBookChapter>) {
        chapters.forEach { resumeStore.clear(it.key); videoResumeStore.clear(it.key) }
        val first = chapters.firstOrNull() ?: return
        val st = playerState.value
        // Načtená první kapitola: playFrom by jen navázal → přetoč přehrávač na začátek (Codex 2026-10-13).
        if (st.isActive && st.currentEpisodeId == first.key) connection.seekToChapter(0.0)
        playFrom(chapters, 0)
    }

    fun markFinished(ch: SeriesBookChapter) {
        resumeStore.markFinished(ch.key)
        videoResumeStore.clear(ch.key)
    }

    fun resetPosition(ch: SeriesBookChapter) {
        connection.ejectIfLoaded(ch.key)
        resumeStore.clear(ch.key)
        videoResumeStore.clear(ch.key)
    }

    fun download(ch: SeriesBookChapter) {
        ch.download?.let(offline::enqueue)
        prepareCtv(listOf(ch))
    }

    /** Stáhni všechny nestažené kapitoly od první (fronta stahování jde po jedné, v pořadí knihy). */
    fun downloadAll(chapters: List<SeriesBookChapter>) {
        val todo = chapters.filter { it.download != null && !offline.isDownloaded(it.key) }
        prepareCtv(todo)
        todo.forEach { offline.enqueue(it.download!!) }
    }

    fun cancelDownloads(chapters: List<SeriesBookChapter>) {
        chapters.forEach { ch ->
            val s = offline.stateFor(ch.key).status
            if (s == OfflineStatus.QUEUED || s == OfflineStatus.DOWNLOADING || s == OfflineStatus.FAILED) offline.cancel(ch.key)
        }
    }

    fun deleteDownload(ch: SeriesBookChapter) = offline.delete(ch.key)

    fun deleteAllDownloads(chapters: List<SeriesBookChapter>) = chapters.forEach { offline.delete(it.key) }

    /** ČT díly server skládá z DASH (~1,5 min) → požádej o přípravu všech dopředu, telefon pak jen stahuje. */
    private fun prepareCtv(chapters: List<SeriesBookChapter>) {
        val ids = chapters.filter { it.key.startsWith("ctv:") && !offline.isDownloaded(it.key) }.map { it.key.removePrefix("ctv:") }
        if (ids.isEmpty()) return
        val base = prefs.getString("uploader_base_url", "") ?: ""
        val cookie = prefs.getString("uploader_session_cookie", "") ?: ""
        viewModelScope.launch { runCatching { uploaderDs.prepareCtvAudioFiles(base, cookie, ids) } }
    }
}
