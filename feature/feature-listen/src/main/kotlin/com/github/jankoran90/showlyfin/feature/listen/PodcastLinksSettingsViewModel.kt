package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.feature.listen.player.PodcastLinkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * TWINE (SHW-74 / plán F7): správa PROPOJENÝCH pořadů (audio+video) v Nastavení → Poslech. Parita
 * Nastavení (HARD RULE): co jde propojit v knihovně, jde i přehledně spravovat/zrušit z Nastavení.
 */
@HiltViewModel
class PodcastLinksSettingsViewModel @Inject constructor(
    private val linkStore: PodcastLinkStore,
    repo: PodcastSourcesRepository,
) : ViewModel() {

    /** Jeden řádek správy: propojený pořad + lidsky popsaní členové. */
    data class LinkRow(val groupId: String, val title: String, val members: List<String>)

    /** Reaktivní seznam propojení s rozluštěnými názvy členských zdrojů. */
    val rows: StateFlow<List<LinkRow>> =
        combine(linkStore.links, repo.sources) { links, sources ->
            val byKey = sources.associateBy { linkStore.key(it) }
            links.map { g ->
                val members = g.members.mapNotNull { byKey[it] }
                LinkRow(
                    groupId = g.id,
                    title = g.title ?: members.firstOrNull()?.title ?: "Propojený pořad",
                    members = members.map { (if (it.type == "youtube") "YouTube: " else "Podcast: ") + it.title },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unlink(groupId: String) = linkStore.unlink(groupId)
}
