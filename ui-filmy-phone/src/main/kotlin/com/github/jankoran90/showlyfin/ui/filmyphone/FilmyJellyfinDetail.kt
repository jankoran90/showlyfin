package com.github.jankoran90.showlyfin.ui.filmyphone

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
 * CELLULOID (SHW-98) M2.6 — telefonní obdoba TV `TvJellyfinDetailRoute` (varianta A: ui-tv se NESAHÁ,
 * proto vlastní tenká kopie). Z pouhého `jellyfinId` dohledá providerIds/typ
 * ([JellyfinLibraryService.getItemMeta]) a deleguje na sdílený [DetailScreen] (fanart hero + akce),
 * kde se titul přes `loadJellyfinOwned` spáruje jako vlastněný → přímé JF přehrání funguje. Fallback na
 * [JellyfinDetailScreen] jen pro položky bez Tmdb i Imdb (čistě lokální soubor bez metadat).
 */
sealed interface FilmyJfResolve {
    data object Loading : FilmyJfResolve
    data class Immersive(val item: MediaItem) : FilmyJfResolve
    data object Fallback : FilmyJfResolve
}

@HiltViewModel
class FilmyJellyfinResolveViewModel @Inject constructor(
    private val library: JellyfinLibraryService,
) : ViewModel() {
    private val _state = MutableStateFlow<FilmyJfResolve>(FilmyJfResolve.Loading)
    val state: StateFlow<FilmyJfResolve> = _state.asStateFlow()

    private var resolvedFor: String? = null

    fun resolve(jellyfinId: String) {
        if (resolvedFor == jellyfinId) return
        resolvedFor = jellyfinId
        _state.value = FilmyJfResolve.Loading
        viewModelScope.launch {
            val meta = library.getItemMeta(jellyfinId)
            _state.value = if (meta != null && (meta.tmdbId != null || meta.imdbId != null)) {
                FilmyJfResolve.Immersive(
                    MediaItem(
                        traktId = 0L,
                        tmdbId = meta.tmdbId,
                        imdbId = meta.imdbId,
                        title = meta.name,
                        year = meta.year,
                        overview = meta.overview,
                        rating = null,
                        genres = null,
                        // Seriál MUSÍ být SHOW, jinak DetailViewModel volá fetchMovieDetails na chybné id.
                        type = if (meta.isSeries) MediaType.SHOW else MediaType.MOVIE,
                    ),
                )
            } else {
                FilmyJfResolve.Fallback
            }
        }
    }
}

@Composable
fun FilmyJellyfinDetail(
    itemId: String,
    onBack: () -> Unit,
    onCollectionPartClick: (CollectionPart) -> Unit,
    onOpenJellyfinDetail: (String) -> Unit,
    onPlayJellyfin: (String) -> Unit,
    onPlayStreamUrl: (String, String, SubtitleQuery?) -> Unit,
    modifier: Modifier = Modifier,
    resolveVm: FilmyJellyfinResolveViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { resolveVm.resolve(itemId) }
    val state by resolveVm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        FilmyJfResolve.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is FilmyJfResolve.Immersive -> DetailScreen(
            item = s.item,
            onBack = onBack,
            onCollectionPartClick = onCollectionPartClick,
            onPlayJellyfin = onPlayJellyfin,
            onPlayStreamUrl = onPlayStreamUrl,
            castInOverflow = true,
            modifier = modifier,
        )
        FilmyJfResolve.Fallback -> JellyfinDetailScreen(
            itemId = itemId,
            onBack = onBack,
            onPlay = onPlayJellyfin,
            onCollectionPartClick = { part -> part.jellyfinId?.let { onOpenJellyfinDetail(it) } },
            modifier = modifier,
        )
    }
}
