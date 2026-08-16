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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    /**
     * PROFIL (2026-08-16) — ostatní dospělí profily → cíle „Sdílet s…" (dlouhý stisk karty epizody).
     * User (2026-08-16 13:49, „nech zobrazit Domů i dětem") — sdílení je jen mezi dospělými (stejný
     * záměr jako u audioknih/zdrojů); z dětského profilu prázdné, ať se dítěti dlouhý stisk netváří
     * jako nabídka „Sdílet".
     */
    val otherAdultProfiles: StateFlow<List<ProfileEntity>> =
        profileRepository.observeAll()
            .combine(profileRepository.activeProfile) { profiles, active ->
                if (active?.isAdmin != true) emptyList()
                else profiles.filter { it.isAdmin && it.id != active.id }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * User (2026-08-16 14:42, „Sdílet s Nel u dětské knihy nedává smysl") — ABS knihovny vyhrazené
     * VÝHRADNĚ dětskému profilu. Vlastnictví/sdílení se na ně nevztahuje (je jen mezi dospělými), ale
     * na rozdíl od dřívějšího 13:29 fixu je NEVYLUČUJEME z Domů úplně (admin, co si sám poslechl kus
     * dětské knihy, se k ní chce vrátit) — jen se pro ně potlačí nabídka „Sdílet s…" v UI.
     */
    val kidsLibraryIds: StateFlow<Set<String>> =
        profileRepository.observeAll()
            .map { profiles ->
                profiles.filterNot { it.isAdmin }
                    .flatMap { ProfileConfig.fromJson(it.configJson).absLibraryWhitelist.orEmpty() }
                    .toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

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

    /** User (2026-08-16) — vzor [ListenViewModel.adultProfileName]/[ownerOfSourceKey]/[ownershipInfoLine]. */
    fun adultProfileName(uuid: String?): String? {
        if (uuid.isNullOrBlank()) return null
        if (uuid == profileRepository.activeProfile.value?.profileUuid) return "já"
        return otherAdultProfiles.value.firstOrNull { it.profileUuid == uuid }?.name
    }

    fun ownerOfSourceKey(key: String): String? =
        sourcesRepo.sources.value.firstOrNull { "${it.type}:${it.ref}" == key }?.addedBy

    fun ownerOfBook(itemId: String): String? = audiobookOwnership.ownership.value[itemId]

    fun ownershipInfoLine(ownerUuid: String?, sharedWithProfiles: List<ProfileEntity>): String {
        val owner = adultProfileName(ownerUuid) ?: "já"
        val sharedNames = sharedWithProfiles.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
        return if (sharedNames.isEmpty()) "V knihovně: $owner"
        else "V knihovně: $owner · sdíleno s: ${sharedNames.joinToString(", ")}"
    }

    /**
     * User (2026-08-16 12:51, „ukončit poslech ať je vidět i na Domů") — vzor [ListenViewModel.resetBookProgress].
     * User (2026-08-16 13:19, „chci live akci, ať je to hned po potvrzení provedené a vidím změnu")
     * — položka mizí z `_items` OKAMŽITĚ (optimisticky), server volání + [refresh] doběhne na pozadí.
     */
    fun resetBookProgress(book: Audiobook) {
        _items.update { list -> list.filterNot { it is ContinueItem.Book && it.book.id == book.id } }
        viewModelScope.launch {
            repo.resetProgress(book.id)
            refresh()
        }
    }

    /** Vzor [ListenViewModel.resetPosition] pro direct epizody — smaže mark, mizí z Domů OKAMŽITĚ. */
    fun resetEpisodeProgress(item: ContinueItem.Episode) {
        _items.update { list -> list - item }
        item.episode.resumeKey?.let { directResume.clear(it) }
        refresh()
    }

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

    /**
     * User (2026-08-16 14:03, „při přepnutí profilu se musí hned překreslit nabídka, ukazuje vždy
     * data předchozího profilu do restartu appky") — root cause: `init { refresh() }` je JEDNORÁZOVÉ,
     * ne reaktivní na aktivní profil. `Activity.recreate()` (viz `ProfileSwitchSignal`/`SlovoPhoneShell`)
     * NEZNIČÍ ViewModelStore (Android to zachovává stejně jako při change konfigurace), takže tahle
     * VM instance přežije rekreaci a `init` se znovu nespustí → visí na starých datech. [ListenViewModel]
     * má stejný vzor („Plan VAULT") už dřív, tady chyběl. `activeProfile.profileUuid` jako klíč, ať se
     * nerefreshuje na každou drobnou změnu configu (na rozdíl od [ListenViewModel] Domů nesleduje
     * knihovní whitelist/creds, jen KDO je aktivní).
     */
    init {
        profileRepository.activeProfile
            .map { it?.profileUuid }
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)

        /**
         * User (2026-08-16 14:57, „poslouchám podcast Cukrfree, na Domů neskočí okamžitě, musím
         * zavřít/otevřít appku") — Domů se dřív přepočítalo jen na `refresh()`, ne reaktivně na nové
         * rozposlouchané epizody. Klíče NEDOKONČENÝCH marek (ne celá mapa/pozice) ať se nerefreshuje
         * na KAŽDÝ update pozice (`DirectResumeStore.save()` běží často během přehrávání) — jen když
         * PŘIBUDE nová epizoda (začal poslouchat) nebo ZMIZÍ (dohráno/reset).
         */
        directResume.marks
            .map { marks -> marks.filterValues { !it.isFinished }.keys }
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /**
     * User (2026-08-16 13:36, „nacita se pokazde 6-10s") — knihy i epizody dřív běžely striktně za
     * sebou (audioknihovny navíc jedna po druhé uvnitř [flatMap]); teď obojí souběžně ([async]).
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val booksDeferred = async {
                runCatching {
                    if (!repo.isConfigured) emptyList()
                    else coroutineScope {
                        repo.getAudiobookLibraries()
                            .map { lib -> async { repo.getAudiobooks(lib.id) } }
                            .awaitAll()
                            .flatten()
                    }
                        .filter { it.progress > 0.001 && !it.isFinished }
                        .let { filterVisibleBooks(it) }
                }.getOrDefault(emptyList())
            }
            val episodesDeferred = async { runCatching { continueDirectEpisodes() }.getOrDefault(emptyList()) }
            _items.value = (booksDeferred.await().map(ContinueItem::Book) + episodesDeferred.await())
                .sortedByDescending { it.updatedAt }
            _isLoading.value = false
        }
    }

    /**
     * PROFIL (2026-08-16) — jen moje audioknihy + co mi kdo sdílel (vzor [ListenViewModel.filterVisibleBooks]).
     * User (2026-08-16 13:49, „nech zobrazit Domů i dětem") — dětský profil nemá koncept vlastnictví/
     * sdílení (ten je jen mezi dospělými), místo toho striktně jen VLASTNÍ knihovna ([ProfileConfig.absLibraryWhitelist]),
     * ať se mu do Domů nepřimíchá dospělácký obsah.
     * User (2026-08-16 14:42, „Baron Prášil a Acid for the Children nejsou vidět na Home, chybí
     * pokračovat") — REGRESE z 13:29 fixu: knihy z dětské knihovny se dřív z adminova Domů úplně
     * vylučovaly (`libraryId !in kidsLibraryIds`), ale to je moc tvrdé — admin, co si sám poslechl
     * kus dětské knihy, chce se k ní z Domů taky vrátit. Skutečný problém byl JEN nesmyslná nabídka
     * „Sdílet s Nel" u takové knihy (řeší se na úrovni UI sheetu, ne mazáním z Domů) — vlastnictví/
     * sdílení se na knihy z dětské knihovny prostě nevztahuje (`isVisible` check pro ně přeskočen).
     */
    private suspend fun filterVisibleBooks(books: List<Audiobook>): List<Audiobook> {
        val active = profileRepository.activeProfile.value ?: return books
        if (!active.isAdmin) {
            val wl = profileRepository.activeConfig.value.absLibraryWhitelist ?: return books
            return books.filter { it.libraryId in wl }
        }
        audiobookOwnership.refresh()
        val shared = profileRepository.activeConfig.value.sharedAudiobookIds
        val kidsIds = kidsLibraryIds.value
        return books.filter {
            it.libraryId in kidsIds || audiobookOwnership.isVisible(it.id, active.profileUuid, shared)
        }
    }

    /**
     * Rozposlouchané direct epizody → dohledané přes feedy zdrojů (stejný join jako CRUISE Android Auto).
     * User (2026-08-16 13:36, „nacita se pokazde 6-10s, chci to hned") — dřív se feedy VŠECH zdrojů
     * natahovaly jedna po druhé ([forEach]) jen kvůli pár markům; teď (1) přeskočí zdroje typu, co v
     * markách vůbec není (`rss:`/`yt:`/`ctv:` prefix klíče), (2) zbylé natáhne SOUBĚŽNĚ ([async]) —
     * čas se zkrátí z „součet všech zdrojů" na „nejpomalejší jeden zdroj".
     */
    private suspend fun continueDirectEpisodes(): List<ContinueItem.Episode> {
        // User (2026-08-16, „doposlouchané zmizí z Domů") — od té doby, co [DirectResumeStore] mark
        // při dohrání NEMAŽE (jen ho nechá na isFinished), musí Domů dohrané výslovně vyfiltrovat.
        val marks = directResume.marks.value.filterValues { !it.isFinished }
        if (marks.isEmpty()) return emptyList()
        sourcesRepo.refresh()
        // Klíč markay má prefix "yt:"/"rss:"/"ctv:" ([DirectResumeStore]), ale PodcastSource.type je
        // "youtube"/"rss"/"ctv" (viz [SourceCard]) — NEJSOU stejné stringy, nutná explicitní mapa.
        val neededTypes = marks.keys.mapNotNullTo(mutableSetOf()) {
            when (it.substringBefore(':', missingDelimiterValue = "")) {
                "yt" -> "youtube"
                "rss" -> "rss"
                "ctv" -> "ctv"
                else -> null
            }
        }
        // User (2026-08-16 13:49, „nech zobrazit Domů i dětem") — dětský profil smí dohledávat jen
        // zdroje, co mu admin schválil (stejná whitelist jako [KidsListenContent]), jinak by Domů
        // ukázalo epizody z dospěláckých zdrojů, co dítě jinde v appce vůbec nevidí.
        val active = profileRepository.activeProfile.value
        val relevant = sourcesRepo.sources.value
            .filter { it.type in neededTypes }
            .let { srcs ->
                if (active?.isAdmin == false) {
                    val visibleKeys = profileRepository.activeConfig.value.visibleForKidsSourceKeys
                    srcs.filter { "${it.type}:${it.ref}" in visibleKeys }
                } else srcs
            }
        val byKey = coroutineScope {
            relevant
                .map { src -> async { src to runCatching { sourcesRepo.loadEpisodes(src) }.getOrDefault(emptyList()) } }
                .awaitAll()
        }.fold(HashMap<String, Pair<SourceEpisode, PodcastSource>>()) { acc, (src, episodes) ->
            episodes.forEach { ep -> ep.resumeKey?.let { acc[it] = ep to src } }
            acc
        }
        return marks.entries.mapNotNull { (key, mark) ->
            byKey[key]?.let { (ep, src) ->
                val progress = if (mark.durMs > 0) (mark.posMs.toFloat() / mark.durMs).coerceIn(0f, 1f) else 0f
                ContinueItem.Episode(src.type, src.ref, src.title, ep, progress, mark.updatedAt)
            }
        }
    }
}
