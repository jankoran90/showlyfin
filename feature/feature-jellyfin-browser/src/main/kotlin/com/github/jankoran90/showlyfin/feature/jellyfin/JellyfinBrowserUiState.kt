package com.github.jankoran90.showlyfin.feature.jellyfin

data class JellyfinLibrary(
    val id: String,
    val name: String,
    val itemCount: Int?,
)

data class JellyfinBrowserUiState(
    val libraries: List<JellyfinLibrary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverUrl: String = "",
    val isConnected: Boolean = false,
)
