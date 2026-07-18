package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.ui.SourceAvailabilityProvider
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CINEMATHEQUE (SHW-98, user 2026-07-18) — napojení [SourceAvailabilityProvider] na [WorkingSourceStore].
 * Vyvěšeno jednou v `FilmyPhoneShell` → karty/řádky ([MediaRow]/[PosterCard]) přes `rememberHasSource`
 * ukazují odznak „má uložený zdroj". `savedKeys` je reaktivní store flow → odznak naskočí živě, jak
 * backfill uloží zdroj (bez restartu / překreslení sekce).
 */
@HiltViewModel
class FilmySourceAvailabilityViewModel @Inject constructor(
    private val store: WorkingSourceStore,
) : ViewModel(), SourceAvailabilityProvider {

    override val savedKeys: StateFlow<Set<String>> = store.savedKeys

    init {
        // Sjednoť lokální index (savedKeys) se serverem/diskem, ať odznaky sedí hned po startu.
        viewModelScope.launch { runCatching { store.refresh() } }
    }
}
