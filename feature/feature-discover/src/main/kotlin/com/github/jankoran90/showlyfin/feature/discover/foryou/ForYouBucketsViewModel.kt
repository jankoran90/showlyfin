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
) : ViewModel() {

    private val _state = MutableStateFlow(ForYouBucketsUiState())
    val state: StateFlow<ForYouBucketsUiState> = _state.asStateFlow()

    private var lastProfileId: Long? = null

    init {
        profileRepository.activeProfile
            .onEach { p -> if (p?.id != lastProfileId) { lastProfileId = p?.id; load() } }
            .launchIn(viewModelScope)
    }

    fun load() {
        _state.value = ForYouBucketsUiState(loading = true)
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
                                val items = curator.forYouBucket(bucket, ROW_LIMIT, pollUntilReady = true)
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
            _state.update { it.copy(loading = false) }
        }
    }

    /** Hotová řada jde do UI hned (prázdnou zahodíme, ale do průběhu se počítá). */
    private fun publish(rail: CuratorRail) {
        _state.update { s ->
            s.copy(
                rails = if (rail.items.isEmpty()) s.rails else s.rails + rail,
                done = s.done + 1,
            )
        }
    }

    private companion object {
        const val ROW_LIMIT = 20
        const val SEED_COUNT = 2
    }
}
