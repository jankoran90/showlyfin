package com.github.jankoran90.showlyfin.feature.discover.trakt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchHistoryUiState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Historie sledování (Trakt `sync/watched`) jako VLASTNÍ obrazovka — user 2026-07-31: „potřebuju někde
 * v telefonu vidět tu historii, abych mohl reagovat hned hodnocením. Nechci hodnotit automaticky po
 * koukání, udělám si to sám přes historii."
 *
 * Dosud šla historie jen jako jedna řada v TV sekci Trakt ([TvTraktViewModel] „Zhlédnuto"); telefon ji
 * neměl nikde. Data přes sdílený [TraktRowLoader.history] (nejnověji sledované první), hodnocení řeší
 * karty samy přes `LocalUserRatingProvider` (dlouhý stisk → hvězdičkový dialog).
 *
 * Přenačtení při změně profilu = vzor [TvTraktViewModel]: jiný profil má jiný Trakt účet, jinak by
 * obrazovka držela historii toho předchozího.
 */
@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val loader: TraktRowLoader,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchHistoryUiState())
    val state: StateFlow<WatchHistoryUiState> = _state.asStateFlow()

    private var lastProfileId: Long? = null

    init {
        profileRepository.activeProfile
            .onEach { p -> if (p?.id != lastProfileId) { lastProfileId = p?.id; load() } }
            .launchIn(viewModelScope)
    }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val items = runCatching { loader.history("all") }.getOrDefault(emptyList())
            _state.update { it.copy(items = items, loading = false) }
        }
    }
}
