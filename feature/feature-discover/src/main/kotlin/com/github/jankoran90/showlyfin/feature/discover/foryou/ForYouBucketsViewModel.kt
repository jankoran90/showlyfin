package com.github.jankoran90.showlyfin.feature.discover.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorBucket
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/** Jedna kategorie doporučení — nese i DŮVOD, proč tam ty filmy jsou. */
data class CuratorRail(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
)

data class ForYouBucketsUiState(
    val rails: List<CuratorRail> = emptyList(),
    val loading: Boolean = true,
    /** Kolik kategorií už doběhlo (mozek je LLM — řady doskakují postupně). */
    val done: Int = 0,
    val total: Int = 0,
    /**
     * user 2026-08-27 („ale ukladejme historii") — dřívější dávky doporučení. Čerstvý výběr starý
     * nepřepíše, jen se postaví nad něj; tohle je to, co zůstává pod ním.
     */
    val history: List<CuratorRail> = emptyList(),
    /** Probíhá ruční Vybrat znovu (odlišené od prvního načtení — obsah zůstává na obrazovce). */
    val refreshing: Boolean = false,
)

/**
 * „Pro tebe" ROZDĚLENÉ DO KATEGORIÍ (user 2026-07-31: „můžeme nějak oddělit kategoricky ty doporučení?
 * … tady jsou tyto filmy, protože jsi koukal na ten a ten film").
 *
 * Místo jednoho nerozlišeného balíku vznikne několik řad, z nichž každá říká PROČ:
 *  - [CuratorBucket] TOP / LOVED / RECENT — do mozku jde vždy jen ta výseč vkusu (server je rozliší
 *    přes `bucket` v cache klíči),
 *  - „Protože jsi viděl <film>" — balíček svázaný JEDNÍM titulem z čerstvé historie (`/curator/similar`).
 *
 * Řady se načítají SOUBĚŽNĚ a zveřejňují se, jakmile doběhnou (`rails` roste průběžně) — mozek počítá
 * desítky sekund a čekat na poslední řadu by znamenalo prázdnou obrazovku. Prázdná kategorie se
 * nezobrazí vůbec.
 */
@HiltViewModel
class ForYouBucketsViewModel @Inject constructor(
    private val curator: CuratorLoader,
    private val traktRows: TraktRowLoader,
    private val profileRepository: ProfileRepository,
    private val history: ForYouHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ForYouBucketsUiState())
    val state: StateFlow<ForYouBucketsUiState> = _state.asStateFlow()

    private var lastProfileId: Long? = null

    init {
        profileRepository.activeProfile
            .onEach { p -> if (p?.id != lastProfileId) { lastProfileId = p?.id; load() } }
            .launchIn(viewModelScope)
    }

    /**
     * [force] = ruční Vybrat znovu (user 2026-08-27 „Jak tohle aktualizuji?"). Bez něj by server
     * vrátil týž výběr — v týdnu je klíč stabilní a odpověď se drží 6 h. Dosavadní řady se přitom
     * NEZAHAZUJÍ: přesunou se do historie pod čerstvou dávku.
     */
    fun load(force: Boolean = false) {
        _state.value = ForYouBucketsUiState(
            loading = !force,
            refreshing = force,
            // Dřívější dávky se ukážou HNED (z disku) — při ručním obnovení tak uživatel nekouká na
            // prázdno těch pár desítek vteřin, co mozek počítá. Předchozí dávku sem NEPŘIDÁVÁM ručně:
            // do archivu se uložila na konci svého vlastního načtení, takže už v něm je.
            history = dedup(loadHistoryRails(), emptySet()),
        )
        viewModelScope.launch {
            // Seedy pro „Protože jsi viděl X" = poslední dokoukané filmy. Bereme je předem, ať víme,
            // kolik řad celkem bude (ukazatel průběhu).
            val seeds = runCatching { traktRows.history("movies") }.getOrDefault(emptyList())
                .filter { it.displayTitle.isNotBlank() }
                .take(SEED_COUNT)
            _state.update { it.copy(total = CuratorBucket.entries.size + seeds.size) }

            coroutineScope {
                val jobs = buildList {
                    CuratorBucket.entries.forEach { bucket ->
                        add(
                            async {
                                val items = curator.forYouBucket(bucket, ROW_LIMIT, pollUntilReady = true, force = force)
                                publish(CuratorRail(bucket.wire, bucket.title, items))
                            },
                        )
                    }
                    seeds.forEach { seed ->
                        add(
                            async {
                                val items = curator.similarTo(
                                    seedTitle = seed.title.ifBlank { seed.displayTitle },
                                    seedYear = seed.year,
                                    limit = ROW_LIMIT,
                                    pollUntilReady = true,
                                    force = force,
                                )
                                publish(
                                    CuratorRail(
                                        id = "similar_${seed.tmdbId ?: seed.displayTitle}",
                                        title = "Protože jsi viděl ${seed.displayTitle}",
                                        items = items,
                                    ),
                                )
                            },
                        )
                    }
                }
                jobs.forEach { it.await() }
            }
            _state.update { it.copy(loading = false, refreshing = false) }
            // Čerstvá dávka jde do archivu taky — po restartu appky tak nezmizí ani ona.
            history.push(_state.value.rails)
        }
    }

    /** Uložené dávky → ploché řady s datem v názvu, nejnovější první. */
    private fun loadHistoryRails(): List<CuratorRail> =
        history.load().flatMap { batch ->
            val stamp = DATE_FORMAT.format(java.util.Date(batch.createdAtMs))
            batch.rails.map { r -> r.copy(id = "hist_${batch.createdAtMs}_${r.id}", title = "${r.title} · $stamp") }
        }

    /**
     * user 2026-08-27 — týž film se objevoval ve dvou řadách naráz: každá kategorie se počítá zvlášť
     * a mezi sebou se nekontrolovaly. Vyhrává řada, která byla dřív (u historie ta novější).
     */
    private fun dedup(rails: List<CuratorRail>, alreadySeen: Set<Long>): List<CuratorRail> {
        val seen = alreadySeen.toMutableSet()
        val out = mutableListOf<CuratorRail>()
        for (rail in rails) {
            // `tmdbId` je z jiného modulu → smart cast nefunguje, potřebuje lokální kopii.
            val items = rail.items.filter { item -> item.tmdbId?.let { seen.add(it) } ?: true }
            if (items.isNotEmpty()) out += rail.copy(items = items)
        }
        return out
    }

    /** Hotová řada jde do UI hned (prázdnou zahodíme, ale do průběhu se počítá). */
    private fun publish(rail: CuratorRail) {
        _state.update { s ->
            // Dedup napříč ČERSTVÝMI řadami (user 2026-08-27: týž film byl ve dvou naráz).
            // 🔴 ZÁMĚRNĚ se NEporovnává proti historii: čerstvá dávka je z velké části tatáž jako
            // ta minulá (v týdnu je výběr stabilní), takže by se sekce vyprázdnila při každém
            // obyčejném otevření. Místo toho se duplicita škrtne v HISTORII — čerstvé má přednost.
            val seen = s.rails.flatMap { r -> r.items.mapNotNull { it.tmdbId } }.toSet()
            val fresh = rail.items.filter { it.tmdbId == null || it.tmdbId !in seen }
            if (fresh.isEmpty()) return@update s.copy(done = s.done + 1)
            val rails = s.rails + rail.copy(items = fresh)
            val freshIds = rails.flatMap { r -> r.items.mapNotNull { it.tmdbId } }.toSet()
            s.copy(rails = rails, history = stripIds(s.history, freshIds), done = s.done + 1)
        }
    }

    /** Vyhoď z historie tituly, které jsou právě v čerstvé dávce; prázdné řady zmizí celé. */
    private fun stripIds(rails: List<CuratorRail>, ids: Set<Long>): List<CuratorRail> =
        rails.mapNotNull { r ->
            val items = r.items.filter { it.tmdbId == null || it.tmdbId !in ids }
            if (items.isEmpty()) null else r.copy(items = items)
        }

    private companion object {
        const val ROW_LIMIT = 20
        // Jeden konkrétní seed („Protože jsi viděl X") stačí — ruční výběr referencí má vlastní sekci
        // „Podle filmu" a každá řada navíc = další výpočet mozku (user 07-31: „kategorie asi méně").
        const val SEED_COUNT = 1
        val DATE_FORMAT = SimpleDateFormat("d. M.", Locale("cs"))
    }
}
