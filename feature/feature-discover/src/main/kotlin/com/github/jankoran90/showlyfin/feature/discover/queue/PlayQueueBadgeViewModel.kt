package com.github.jankoran90.showlyfin.feature.discover.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.db.repository.PlayQueueRepository
import com.github.jankoran90.showlyfin.core.ui.PlayQueueProvider
import com.github.jankoran90.showlyfin.core.ui.playQueueKey
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * RAMPA (SHW-121) — zdroj značky „ve frontě" pro karty (user 2026-08-28: *„Chci taky vidět na coveru
 * nějaky indikator že je v seznamu k prehrani."*).
 *
 * Leží ve feature vrstvě schválně: **telefon i TV** si ho zavěsí přes `LocalPlayQueueProvider` nad
 * svůj shell a všechny karty pak značku umí bez jediné změny v obrazovkách. Stejný vzor jako odznak
 * „má uložený zdroj".
 */
@HiltViewModel
class PlayQueueBadgeViewModel @Inject constructor(
    queue: PlayQueueRepository,
) : ViewModel(), PlayQueueProvider {

    override val queuedKeys: StateFlow<Set<String>> = queue.observe()
        .map { items ->
            items.mapNotNull { playQueueKey(it.id, it.kind == FavoriteKind.QUEUE_SHOW) }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}
