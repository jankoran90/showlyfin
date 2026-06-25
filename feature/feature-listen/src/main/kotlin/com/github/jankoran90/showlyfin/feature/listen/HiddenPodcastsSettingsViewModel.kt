package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WEFT (SHW-75/W5): správa per-profil SKRYTÝCH pořadů v Nastavení → Poslech. Skrýt jde dlouhým stiskem
 * karty (Sledované) nebo z ⋮ menu řádku (časová osa); tady se přehledně OBNOVÍ (parita Nastavení,
 * HARD RULE). Dvě nezávislé dimenze: skryté na časové ose / skryté ve Sledovaných.
 */
@HiltViewModel
class HiddenPodcastsSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    repo: PodcastSourcesRepository,
) : ViewModel() {

    /** Jeden skrytý pořad: klíč `type:ref`/`abs:id` + lidský název. */
    data class HiddenRow(val key: String, val label: String)

    /** Skryté pořady aktivního profilu, rozdělené dle dimenze. */
    data class HiddenState(val onTimeline: List<HiddenRow>, val inFollowing: List<HiddenRow>)

    /** Reaktivní stav — přepočítá se při změně skrytí i při přepnutí profilu / seznamu zdrojů. */
    val state: StateFlow<HiddenState> =
        combine(profileRepository.activeConfig, repo.sources) { cfg, sources ->
            val byKey = sources.associateBy { "${it.type}:${it.ref}" }
            fun row(key: String) = HiddenRow(
                key = key,
                label = byKey[key]?.title
                    ?: if (key.startsWith("abs:")) "Pořad z knihovny" else key.substringAfter(':'),
            )
            HiddenState(
                onTimeline = cfg.hiddenTimelineSourceKeys.map(::row).sortedBy { it.label.lowercase() },
                inFollowing = cfg.hiddenFollowingSourceKeys.map(::row).sortedBy { it.label.lowercase() },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HiddenState(emptyList(), emptyList()))

    /** Obnoví (odkryje) pořad v dané dimenzi pro aktivní profil. */
    fun unhide(key: String, timeline: Boolean) {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            profileRepository.updateConfig(profileId) { c ->
                if (timeline) c.copy(hiddenTimelineSourceKeys = c.hiddenTimelineSourceKeys - key)
                else c.copy(hiddenFollowingSourceKeys = c.hiddenFollowingSourceKeys - key)
            }
        }
    }
}
