package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.data.uploader.CuratorBlocklistStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * SPOTLIGHT+ (user 2026-08-27: „Tohle mi nenabízej") — obsluha černé listiny kurátora.
 * Sdílí ji karta v „Pro tebe" (zablokovat) i blok v Nastavení (odblokovat).
 */
@HiltViewModel
class FilmyBlocklistViewModel @Inject constructor(
    private val store: CuratorBlocklistStore,
) : ViewModel() {

    val items: StateFlow<List<CuratorBlocklistStore.Blocked>> = store.items

    fun block(item: MediaItem) {
        val id = item.tmdbId ?: return
        store.block(id, item.displayTitle.ifBlank { item.title }, item.year)
    }

    fun unblock(tmdbId: Long) = store.unblock(tmdbId)

    fun isBlocked(tmdbId: Long?) = store.isBlocked(tmdbId)
}
