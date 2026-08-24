package com.github.jankoran90.showlyfin.ui.tv.jellyfin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinDetailScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TENFOOT KOLO2 (N) — sjednocení Jellyfin detailu na TV. Každý vstup na Jellyfin obsah (home owned řada,
 * browser knihovny, proklik kolekce) míří na [com.github.jankoran90.showlyfin.ui.tv.TvDestination.JellyfinDetail];
 * tento wrapper z pouhého `jellyfinId` dohledá providerIds/typ ([JellyfinLibraryService.getItemMeta]) a
 * deleguje na NATIVNÍ immersive [DetailScreen] (fanart hero + akce + nativní řada epizod u seriálů). Titul se
 * v [DetailScreen] přes `loadJellyfinOwned` spáruje jako vlastněný → přímé Jellyfin přehrání funguje.
 * Fallback na telefonní [JellyfinDetailScreen] zůstává jen pro položky bez Tmdb i Imdb (čistě lokální soubor).
 */
sealed interface JfResolve {
    data object Loading : JfResolve
    /** [focusSeason]/[focusEpisode] = díl, přes který se sem přišlo (klik v řadě „Další díly"). */
    data class Immersive(
        val item: MediaItem,
        val focusSeason: Int? = null,
        val focusEpisode: Int? = null,
    ) : JfResolve
    data object Fallback : JfResolve
}

@HiltViewModel
class TvJellyfinResolveViewModel @Inject constructor(
    private val library: JellyfinLibraryService,
) : ViewModel() {
    private val _state = MutableStateFlow<JfResolve>(JfResolve.Loading)
    val state: StateFlow<JfResolve> = _state.asStateFlow()

    private var resolvedFor: String? = null

    fun resolve(jellyfinId: String) {
        if (resolvedFor == jellyfinId) return
        resolvedFor = jellyfinId
        _state.value = JfResolve.Loading
        viewModelScope.launch {
            // 🔴 EPIZODA SE NIKDY NEOTEVÍRÁ SAMA ZA SEBE (user 2026-08-24: díl „Panák" seriálu
            // Bluey otevřel cizí FILM „Panák"). `providerIds.Tmdb` je u epizody id DÍLU a typ není
            // SERIES → dřív z toho vyšel film s cizím id. [resolveDetailTarget] vrátí kartu SERIÁLU
            // a díl jen označí; nedohledatelný seriál spadne na jellyfinový detail dílu.
            val target = library.resolveDetailTarget(jellyfinId)
            val meta = target?.meta
            _state.value = if (meta != null && !meta.isEpisode && (meta.tmdbId != null || meta.imdbId != null)) {
                JfResolve.Immersive(
                    MediaItem(
                        traktId = 0L,
                        tmdbId = meta.tmdbId,
                        imdbId = meta.imdbId,
                        title = meta.name,
                        year = meta.year,
                        overview = meta.overview,
                        rating = null,
                        genres = null,
                        // Klíčové: seriál musí být SHOW, jinak DetailViewModel volá fetchMovieDetails na TV id.
                        type = if (meta.isSeries) MediaType.SHOW else MediaType.MOVIE,
                    ),
                    focusSeason = target.focusSeason,
                    focusEpisode = target.focusEpisode,
                )
            } else {
                JfResolve.Fallback
            }
        }
    }
}

@Composable
fun TvJellyfinDetailRoute(
    itemId: String,
    onBack: () -> Unit,
    onCollectionPartClick: (CollectionPart) -> Unit,
    onPlayJellyfin: (String) -> Unit,
    onPlayStreamUrl: (String, String, SubtitleQuery?) -> Unit,
    onOpenEpisodes: (seriesId: String, name: String) -> Unit,
    onOpenJellyfinDetail: (String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    resolveVm: TvJellyfinResolveViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { resolveVm.resolve(itemId) }
    val state by resolveVm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        JfResolve.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is JfResolve.Immersive -> DetailScreen(
            item = s.item,
            onBack = onBack,
            onCollectionPartClick = onCollectionPartClick,
            onPlayJellyfin = onPlayJellyfin,
            onPlayStreamUrl = onPlayStreamUrl,
            onOpenSettings = onOpenSettings,
            focusSeason = s.focusSeason,
            focusEpisode = s.focusEpisode,
            modifier = modifier,
        )
        JfResolve.Fallback -> JellyfinDetailScreen(
            itemId = itemId,
            onBack = onBack,
            onPlay = onPlayJellyfin,
            onOpenEpisodes = onOpenEpisodes,
            onCollectionPartClick = { part -> part.jellyfinId?.let { onOpenJellyfinDetail(it) } },
            modifier = modifier,
        )
    }
}
