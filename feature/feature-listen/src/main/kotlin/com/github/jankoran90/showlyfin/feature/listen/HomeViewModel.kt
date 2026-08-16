package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.uploader.AudiobookOwnershipRepository
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Obrazovka „Domů" (user 2026-08-15: „naposledy přehráno a pokračovat, hezky v mřížce, vždy první").
 * Sjednocuje VŠECHNO rozposlouchané napříč zdroji: ABS audioknihy (`Audiobook.lastUpdate`) + direct
 * epizody RSS/YouTube/ČT ([DirectResumeStore], titul/cover dohledán přes feed zdroje — resumeKey je
 * sjednocený s Android Auto „Pokračovat", viz `AudiobookBrowseTree.continueItems()`, stejná logika,
 * jiný sink (Compose grid místo MediaItem stromu)). Seřazeno podle posledního poslechu, nejnovější první.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: AbsRepository,
    private val sourcesRepo: PodcastSourcesRepository,
    private val directResume: DirectResumeStore,
    private val profileRepository: ProfileRepository,
    private val audiobookOwnership: AudiobookOwnershipRepository,
    connection: AudiobookPlayerConnection,
) : ViewModel() {

    /** User (2026-08-15 16:49) — odznak „hraje" na dlaždici, když je zrovna aktivní v přehrávači. */
    val playerState = connection.state

    /** PROFIL (2026-08-16) — ostatní dospělí profily → cíle „Sdílet s…" (dlouhý stisk karty epizody). */
    val otherAdultProfiles: StateFlow<List<ProfileEntity>> =
        profileRepository.observeAll()
            .combine(profileRepository.activeProfile) { profiles, active ->
                profiles.filter { it.isAdmin && it.id != active?.id }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun isSourceSharedWith(keys: Set<String>, target: ProfileEntity): Boolean {
        val cfg = ProfileConfig.fromJson(target.configJson)
        return keys.any { it in cfg.sharedSourceKeys }
    }

    fun setSourceSharedWith(keys: Set<String>, targetId: Long, shared: Boolean) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            profileRepository.updateConfig(targetId) { cfg ->
                val s = cfg.sharedSourceKeys.toMutableSet()
                    .also { if (shared) it.addAll(keys) else it.removeAll(keys) }
                cfg.copy(sharedSourceKeys = s)
            }
        }
    }

    /** PROFIL (2026-08-16) — audiokniha (vzor sources, klíč = ABS itemId). */
    fun isBookSharedWith(itemId: String, target: ProfileEntity): Boolean =
        itemId in ProfileConfig.fromJson(target.configJson).sharedAudiobookIds

    fun setBookSharedWith(itemId: String, targetId: Long, shared: Boolean) {
        viewModelScope.launch {
            profileRepository.updateConfig(targetId) { cfg ->
                val s = cfg.sharedAudiobookIds.toMutableSet()
                    .also { if (shared) it.add(itemId) else it.remove(itemId) }
                cfg.copy(sharedAudiobookIds = s)
            }
        }
    }

    sealed interface ContinueItem {
        val updatedAt: Long

        data class Book(val book: Audiobook) : ContinueItem {
            override val updatedAt: Long = book.lastUpdate ?: 0L
        }

        data class Episode(
            val sourceType: String,
            val sourceRef: String,
            val sourceTitle: String,
            val episode: SourceEpisode,
            val progress: Float,
            override val updatedAt: Long,
        ) : ContinueItem
    }

    private val _items = MutableStateFlow<List<ContinueItem>>(emptyList())
    val items: StateFlow<List<ContinueItem>> = _items.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val books = runCatching {
                if (!repo.isConfigured) emptyList()
                else repo.getAudiobookLibraries()
                    .flatMap { repo.getAudiobooks(it.id) }
                    .filter { it.progress > 0.001 && !it.isFinished }
                    .let { filterVisibleBooks(it) }
            }.getOrDefault(emptyList())
            val episodes = runCatching { continueDirectEpisodes() }.getOrDefault(emptyList())
            _items.value = (books.map(ContinueItem::Book) + episodes)
                .sortedByDescending { it.updatedAt }
            _isLoading.value = false
        }
    }

    /** PROFIL (2026-08-16) — jen moje audioknihy + co mi kdo sdílel (vzor [ListenViewModel.filterVisibleBooks]). */
    private suspend fun filterVisibleBooks(books: List<Audiobook>): List<Audiobook> {
        val active = profileRepository.activeProfile.value ?: return books
        if (!active.isAdmin) return books
        audiobookOwnership.refresh()
        val shared = profileRepository.activeConfig.value.sharedAudiobookIds
        return books.filter { audiobookOwnership.isVisible(it.id, active.profileUuid, shared) }
    }

    /** Rozposlouchané direct epizody → dohledané přes feedy zdrojů (stejný join jako CRUISE Android Auto). */
    private suspend fun continueDirectEpisodes(): List<ContinueItem.Episode> {
        // User (2026-08-16, „doposlouchané zmizí z Domů") — od té doby, co [DirectResumeStore] mark
        // při dohrání NEMAŽE (jen ho nechá na isFinished), musí Domů dohrané výslovně vyfiltrovat.
        val marks = directResume.marks.value.filterValues { !it.isFinished }
        if (marks.isEmpty()) return emptyList()
        sourcesRepo.refresh()
        val byKey = HashMap<String, Pair<SourceEpisode, PodcastSource>>()
        sourcesRepo.sources.value.forEach { src ->
            runCatching { sourcesRepo.loadEpisodes(src) }.getOrDefault(emptyList()).forEach { ep ->
                ep.resumeKey?.let { byKey[it] = ep to src }
            }
        }
        return marks.entries.mapNotNull { (key, mark) ->
            byKey[key]?.let { (ep, src) ->
                val progress = if (mark.durMs > 0) (mark.posMs.toFloat() / mark.durMs).coerceIn(0f, 1f) else 0f
                ContinueItem.Episode(src.type, src.ref, src.title, ep, progress, mark.updatedAt)
            }
        }
    }
}
