package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.Podcast
import com.github.jankoran90.showlyfin.data.abs.model.PodcastDetail

/** Přepínač obsahu poslechové sekce. */
enum class ListenMode { BOOKS, PODCASTS }

data class ListenUiState(
    val isConfigured: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val mode: ListenMode = ListenMode.BOOKS,
    // audioknihy
    val libraries: List<AbsLibrary> = emptyList(),
    val selectedLibraryId: String? = null,
    val books: List<Audiobook> = emptyList(),
    // podcasty (lazy: načtou se až při prvním přepnutí na Podcasty)
    val podcastLibraries: List<AbsLibrary> = emptyList(),
    val selectedPodcastLibraryId: String? = null,
    val podcasts: List<Podcast> = emptyList(),
    val podcastsLoaded: Boolean = false,
)

data class AudiobookDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: com.github.jankoran90.showlyfin.data.abs.model.AudiobookDetail? = null,
)

data class PodcastDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: PodcastDetail? = null,   // epizody již profiltrované dle hideFinished
    val hideFinished: Boolean = false,
)

/** Stav sheetu „Prohledat epizody" — dostupné RSS epizody k stažení na ABS server. */
data class FindEpisodesState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val episodes: List<com.github.jankoran90.showlyfin.data.abs.model.FeedEpisode> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val error: String? = null,
    val submitting: Boolean = false,
    val resultMessage: String? = null,   // hláška po odeslání (snackbar)
)
