package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook

data class ListenUiState(
    val isConfigured: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val libraries: List<AbsLibrary> = emptyList(),
    val selectedLibraryId: String? = null,
    val books: List<Audiobook> = emptyList(),
)

data class AudiobookDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: com.github.jankoran90.showlyfin.data.abs.model.AudiobookDetail? = null,
)
