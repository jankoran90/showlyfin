package com.github.jankoran90.showlyfin.feature.jellyfin

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class EpisodePickerViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(EpisodePickerUiState())
    val state: StateFlow<EpisodePickerUiState> = _state.asStateFlow()

    fun load(seriesId: String, seriesName: String) {
        _state.update { it.copy(isLoading = true, error = null, seriesName = seriesName) }
        viewModelScope.launch {
            val serverUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val token = prefs.getString("jellyfin_token", "") ?: ""
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""
            if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) {
                _state.update { it.copy(isLoading = false, error = "Jellyfin není nastaven") }
                return@launch
            }
            try {
                apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
                val userUuid = UUID.fromString(userId)
                val seriesUuid = UUID.fromString(seriesId)

                val nextUpId = runCatching {
                    apiClient.tvShowsApi.getNextUp(userId = userUuid, seriesId = seriesUuid).content.items
                        .firstOrNull()?.id?.toString()
                }.getOrNull()

                val episodes = apiClient.tvShowsApi.getEpisodes(
                    seriesId = seriesUuid,
                    userId = userUuid,
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                ).content.items.map { ep ->
                    val epId = ep.id.toString()
                    EpisodeRow(
                        id = epId,
                        seasonNumber = ep.parentIndexNumber,
                        episodeNumber = ep.indexNumber,
                        name = ep.name ?: "Epizoda",
                        imageUrl = "$serverUrl/Items/$epId/Images/Primary?fillWidth=320&quality=85&api_key=$token",
                        overview = ep.overview,
                        watched = ep.userData?.played == true,
                        progressPct = ep.userData?.playedPercentage?.toInt(),
                        isNextUp = nextUpId != null && epId == nextUpId,
                    )
                }
                // Fallback: pokud Next Up nevrátil nic, zvýrazni první nezhlédnutou.
                val withNextUp = if (episodes.none { it.isNextUp }) {
                    val idx = episodes.indexOfFirst { !it.watched }
                    if (idx >= 0) episodes.mapIndexed { i, e -> if (i == idx) e.copy(isNextUp = true) else e } else episodes
                } else {
                    episodes
                }
                val nextUpIndex = withNextUp.indexOfFirst { it.isNextUp }
                _state.update {
                    it.copy(isLoading = false, episodes = withNextUp, nextUpIndex = nextUpIndex, error = null)
                }
            } catch (e: Throwable) {
                Timber.w(e, "[EpisodePicker] load failed")
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba načtení epizod") }
            }
        }
    }
}
