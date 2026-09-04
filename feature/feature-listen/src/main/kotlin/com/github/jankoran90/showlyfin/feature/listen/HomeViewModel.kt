package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.toAudiobook
import com.github.jankoran90.showlyfin.data.uploader.AudiobookOwnershipRepository
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
    private val audiobookDownloads: AudiobookDownloadManager,
    private val sourcesRepo: PodcastSourcesRepository,
    private val directResume: DirectResumeStore,
    // BUG (2026-09-04, user „na domov sekci rozposlouchané epizody podcastu nejsou"): video (YouTube/
    // ČT) watch pozice žila jen v [com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore],
    // Domů o ní vůbec nevěděla — rozkoukané video se tu nikdy neobjevilo, i teď zapisuje do stejného klíče.
    private val videoResume: com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore,
    // BUG (2026-09-04): stažená epizoda ať hraje z lokálního souboru i po ťuku z Domů (parita s
    // PodcastSearchViewModel.toQueued/YoutubeChannelViewModel).
    private val offline: com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager,
    private val profileRepository: ProfileRepository,
    private val audiobookOwnership: AudiobookOwnershipRepository,
    private val absPrefs: AbsPreferences,
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
        audiobookDownloads.clearLocalProgress(book.id)
        viewModelScope.launch {
            repo.endListening(book.id, book.progressId)
            refresh()
        }
    }

    /** Vzor [ListenViewModel.resetPosition] pro direct epizody — smaže mark, mizí z Domů OKAMŽITĚ. */
    /** BUG (2026-09-04, user „dej možnost zobrazit je a rovnou naskočit"): ťuk na rozposlouchanou
     *  epizodu na Domů rovnou přehraje (audio poslechový přehrávač), místo aby jen otevřel zdrojovou
     *  obrazovku. Video-tracked pozice (viz [videoResume]) žije v jiném uložišti než audio, takže
     *  „naskočit" tu vždy znamená poslech — video-přesná návaznost je Known gap. */
    fun playEpisode(item: ContinueItem.Episode) {
        val ep = item.episode
        val key = ep.resumeKey ?: ep.id
        val localUrl = offline.localVideo(key)?.let { android.net.Uri.fromFile(it).toString() }
        connection.playDirectEpisode(
            QueuedEpisode(
                itemId = ep.sourceKey?.substringAfter(':') ?: ep.subtitle ?: item.sourceRef,
                episodeId = key,
                title = ep.title,
                coverUrl = ep.imageUrl,
                description = ep.description,
                podcastTitle = item.sourceTitle,
                direct = DirectAudio(url = localUrl ?: ep.streamUrl, durationSec = ep.durationSec, author = item.sourceTitle),
            ),
        )
    }

    fun resetEpisodeProgress(item: ContinueItem.Episode) {
        _items.update { list -> list - item }
        // BUG (2026-09-04): smaž i video pozici — jinak by karta „skončit poslech" na rozkoukaném
        // videu zmizela z Domů jen na chvíli (video mark ji další refresh vrátí zpátky).
        item.episode.resumeKey?.let { directResume.clear(it); videoResume.clear(it) }
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
     * User (2026-08-16 15:00, „vyhodím z Home audioknihu a pořád tam visí") — VÍC nezávislých
     * spouštěčů refreshe (profil, marky, ruční akce jako ukončit poslech) mohlo běžet SOUBĚŽNĚ;
     * starší (pomalejší) běh mohl dopsat `_items` PO novějším a přepsat ho zpátky na neaktuální data
     * (classic poslední-zápis-vyhrává race). [refreshRequests] + `collectLatest` zaručí, že běží
     * vždy jen JEDEN refresh najednou — nový požadavek zruší předchozí ještě nedoběhlý.
     * User (2026-08-16 15:24, „acid for the children pořád visí, žádné živé překreslení") — DOOPRAVDY
     * ZAVINĚNO tímhle fixem: server měl progress správně na 0 (ověřeno v DB), ale appka se k tomu
     * nedostala — při rychlém testování (klik, přehrání, další klik) přicházely požadavky rychleji,
     * než stihl doběhnout síťový fetch (víc knihoven + `getMe()` + zdroje) → `collectLatest` je pořád
     * dokola RUŠIL, než jediný stihl dopsat `_items` → LIVELOCK, appka trvale visela na starých datech.
     * `debounce(300)` počká na chvilku klidu mezi požadavky, než refresh doopravdy spustí; navíc obyčejné
     * `collect` (NE `collectLatest`) — jednou spuštěný refresh se už NIKDY neruší, doběhne vždy do konce
     * (mezitím příchozí požadavky se jen naskládají do bufferu a spustí JEDEN další běh hned po dokončení).
     * User (2026-08-16 15:36, „zmizí na vteřinu a je hned zpět") — DRUHÁ vrstva stejného problému:
     * `collect`/„doběhne vždy do konce" garantuje, že refresh NIKDY nezůstane viset (žádný livelock),
     * ale sám o sobě NEZARUČÍ ČERSTVOST — refresh spuštěný TĚSNĚ PŘED „ukončit poslech" může dokončit
     * síťový fetch (víc knihoven, pár sekund) AŽ PO reset PATCHi a přepsat optimisticky smazanou
     * položku zpátky daty, co ještě neviděla reset. [refreshGeneration] řeší i tohle: běh na konci
     * zapíše `_items` JEN pokud mezitím nezačal žádný novější požadavek — jinak výsledek zahodí (příští
     * fronta ve frontě už doběhne nad čerstvými daty).
     */
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var refreshGeneration = 0L

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
        viewModelScope.launch {
            refreshRequests.debounce(300).collect { doRefresh() }
        }

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

        /**
         * User (2026-08-16 15:24, „dal jsem Acid for... přehrát a pak Domů, tam vůbec nebyl, až po
         * restartu appky") — [directResume.marks] pokrývá jen DIRECT epizody (RSS/YouTube/ČT); nová
         * audiokniha nemá žádný lokální reaktivní signál (progress žije jen na ABS serveru, žádný
         * lokální push) — bez tohohle triggeru se Domů o novém poslechu audioknihy dozví JEN na
         * `init` (nový VM = restart appky). Sleduje se `currentItemId` z přehrávače (mění se, jakmile
         * začne hrát jiná položka), ne pozice — nerefreshuje na každý tick přehrávání.
         *
         * User (2026-08-20 12:01, „kluk poslouchá Hurvínka, na rozposlouchaných není, dokud
         * neresetuju appku") — tenhle refresh() sám o sobě přijde MOC BRZY: `AudiobookPlayerService.
         * startSync()` čeká celý `syncIntervalSeconds` (výchozí 15 s, nastavitelné 5–60 s), než pošle
         * PRVNÍ pozici na ABS server (`while(true){ delay(interval); syncNow() }` — delay je PŘED
         * prvním syncem). V okamžiku téhle refresh() má tedy server pořád progress 0 → `doRefresh()`
         * knihu vyfiltruje (`progress > 0.001`) a nic víc už znovu nezkusí, dokud se nezmění profil/
         * marky/currentItemId znovu — appka pak visí na starých datech přesně jako user popsal.
         * Druhý, ODLOŽENÝ refresh (o `syncIntervalSeconds + 2s` později) domů dožene stav TĚSNĚ PO
         * prvním serverovém syncu, bez nutnosti restartu appky. Vlastní `viewModelScope.launch` (ne
         * blokovat tenhle collector) — přehrávání se může mezitím přepnout na jinou položku, tenhle
         * followup pak jen zbytečně (ale neškodně) refreshne nad už aktuálním stavem.
         */
        connection.state
            .map { it.currentItemId }
            .distinctUntilChanged()
            .onEach { itemId ->
                refresh()
                if (itemId != null) {
                    viewModelScope.launch {
                        delay((absPrefs.syncIntervalSeconds + 2) * 1000L)
                        refresh()
                    }
                }
            }
            .launchIn(viewModelScope)

        /**
         * User (2026-08-20 12:06, „děti poslouchají striktně offline, musí to fungovat i v tomto
         * režimu") — výše uvedený odložený refresh je odvozený od `syncIntervalSeconds`, což je
         * ABS SERVEROVÝ interval; na trvale offline zařízení žádný ABS sync neproběhne vůbec
         * (`AudiobookPlayerConnection.pushState`, `sessionId(extras).isNullOrBlank()` větev zapisuje
         * jen LOKÁLNĚ přes `audiobookDownloads.updateLocalProgress`, `repo.syncProgress` na server se
         * tam vůbec nevolá). Ten offline zápis MÁ vlastní reaktivní zdroj ([AudiobookDownloadManager.
         * downloads], `refreshDownloads()` po každém `updateLocalProgress`) — sleduj ho stejným vzorem
         * jako [directResume.marks] výše, ať se Domů na stažené (offline) audioknize dozví do pár
         * vteřin (throttle zápisu pozice `DIRECT_SAVE_INTERVAL_MS` = 4 s), ne až po restartu appky.
         */
        audiobookDownloads.downloads
            .map { list -> list.filter { it.localPositionSec > 0.5 && !it.localIsFinished }.map { it.itemId }.toSet() }
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /**
     * User (2026-08-16 13:36, „nacita se pokazde 6-10s") — knihy i epizody dřív běžely striktně za
     * sebou (audioknihovny navíc jedna po druhé uvnitř [flatMap]); teď obojí souběžně ([async]).
     * Volání jen POŽÁDÁ o refresh ([refreshRequests]) — skutečný běh (s debounce + garancí doběhnutí)
     * řeší [init].
     */
    fun refresh() {
        refreshGeneration++
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun doRefresh() {
        val myGeneration = refreshGeneration
        coroutineScope {
            _isLoading.value = true
            val booksDeferred = async {
                runCatching {
                    val fetched = if (!repo.isConfigured) emptyList()
                    else coroutineScope {
                        repo.getAudiobookLibraries()
                            .map { lib -> async { repo.getAudiobooks(lib.id) } }
                            .awaitAll()
                            .flatten()
                    }
                    // User (2026-08-16 17:38/17:56, „ať si to rozumí s online stavem a nepere se to")
                    // — network vždy vyhrává tam, kde ho MÁME (server je zdroj pravdy); jen dopočte
                    // libraryId/seriesName do lokálního download indexu (chybí tam, když se stahovalo
                    // z detailu, nebo u starších stažení před 1.0.48), ať offline i online cesta
                    // vidí STEJNÝ výsledek u téže knihy, ne dvě různá zařazení.
                    fetched.forEach { audiobookDownloads.backfillMetadata(it) }
                    // User (2026-08-20 12:33, „proč skáče k dětem na Domov data z dospělý profil??")
                    // — `filterVisibleBooks` se dřív aplikoval JEN na `offlineExtra` (offline-only
                    // knihy), síťová větev proletěla BEZ profilového filtru → na zařízení s internetem
                    // viděl dětský profil úplně VŠECHNY rozposlouchané knihy napříč knihovnami (i
                    // adminovy osobní), protože appka jede na jednom sdíleném ABS admin účtu a profilové
                    // omezení je čistě appkové (client-side), ne serverové. Fix: filtr na obě větve.
                    filterVisibleBooks(fetched.filter { it.progress > 0.001 && !it.isFinished })
                }.getOrDefault(emptyList())
            }
            val episodesDeferred = async { runCatching { continueDirectEpisodes() }.getOrDefault(emptyList()) }
            val networkBooks = booksDeferred.await()
            // User (2026-08-16 17:38, „Domů je offline prázdné, nezaznamenává změny") — knihy STAŽENÉ
            // pro offline s lokálně uloženým rozposlechem (viz `AudiobookPlayerConnection`). Union s
            // network výsledkem, ne náhrada: síťová položka (server progres) má přednost, offline
            // záznam doplní jen to, o čem server (ještě/offline vůbec) neví — na trvale offline
            // zařízení `networkBooks` vždy prázdné (síť selhala), tady jediný zdroj Domů.
            val networkIds = networkBooks.mapTo(mutableSetOf()) { it.id }
            val offlineExtra = runCatching {
                audiobookDownloads.downloads.value
                    .filter { it.itemId !in networkIds && it.localPositionSec > 0.5 && !it.localIsFinished }
                    .map { it.toAudiobook() }
                    .let { filterVisibleBooks(it) }
            }.getOrDefault(emptyList())
            val result = ((networkBooks + offlineExtra).map(ContinueItem::Book) + episodesDeferred.await())
                .sortedByDescending { it.updatedAt }
            // Zahoď zastaralý výsledek — mezitím vznikl novější požadavek (refresh()/reset), jehož
            // vlastní běh dopíše čerstvá data sám; tenhle by je jen přepsal starými.
            if (refreshGeneration == myGeneration) _items.value = result
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
        val audioMarks = directResume.marks.value.filterValues { !it.isFinished }
        // BUG (2026-09-04): video ([VideoResumeStore]) nemá isFinished — store se sám smaže při
        // dohrání (viz jeho dokumentace), takže přítomnost marky = rozkoukáno, žádný filtr netřeba.
        val videoMarks = videoResume.marks.value
        val keys = audioMarks.keys + videoMarks.keys
        if (keys.isEmpty()) return emptyList()
        sourcesRepo.refresh()
        // Klíč markay má prefix "yt:"/"rss:"/"ctv:" ([DirectResumeStore]), ale PodcastSource.type je
        // "youtube"/"rss"/"ctv" (viz [SourceCard]) — NEJSOU stejné stringy, nutná explicitní mapa.
        val neededTypes = keys.mapNotNullTo(mutableSetOf()) {
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
        // BUG (2026-09-04): video má přednost (sdílený klíč = „poslední vyhrává", stejná konvence jako
        // v obrazovkách zdrojů — RssPodcastScreen/YoutubeChannelScreen/CtvProgramScreen).
        return keys.mapNotNull { key ->
            byKey[key]?.let { (ep, src) ->
                val vm = videoMarks[key]
                val am = audioMarks[key]
                val posMs = vm?.posMs ?: am?.posMs ?: return@let null
                val durMs = vm?.durMs ?: am?.durMs ?: 0L
                val updatedAt = vm?.updatedAt ?: am?.updatedAt ?: 0L
                val progress = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
                ContinueItem.Episode(src.type, src.ref, src.title, ep, progress, updatedAt)
            }
        }
    }
}
