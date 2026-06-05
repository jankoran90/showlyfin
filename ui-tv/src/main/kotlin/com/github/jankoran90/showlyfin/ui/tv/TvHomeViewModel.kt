package com.github.jankoran90.showlyfin.ui.tv

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayCommand
import org.jellyfin.sdk.model.api.PlayMessage
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(TvHomeUiState())
    val state: StateFlow<TvHomeUiState> = _state.asStateFlow()

    private val _playEvents = MutableSharedFlow<PlayMessageEvent>(extraBufferCapacity = 1)
    val playEvents: SharedFlow<PlayMessageEvent> = _playEvents.asSharedFlow()

    private var socketSetup = false

    init {
        loadItems()
    }

    fun setFilter(filter: BaseItemKind?) {
        _state.update { it.copy(filter = filter) }
        loadItems()
    }

    fun reload() = loadItems()

    private fun loadItems() {
        viewModelScope.launch {
            val serverUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val token = prefs.getString("jellyfin_token", "") ?: ""
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""

            if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) {
                _state.update { it.copy(isLoading = false, isNotConfigured = true) }
                return@launch
            }

            _state.update { it.copy(isLoading = true, isNotConfigured = false, error = null) }
            try {
                apiClient.update(
                    baseUrl = serverUrl,
                    accessToken = token,
                    clientInfo = clientInfo,
                    deviceInfo = deviceInfo,
                )
                val userUuid = UUID.fromString(userId)
                val rows = mutableListOf<TvHomeRow>()
                val filter = _state.value.filter

                if (filter == null) {
                    runCatching {
                        apiClient.userLibraryApi.getResumeItems(
                            userId = userUuid,
                            limit = 20,
                            fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                            mediaTypes = listOf(MediaType.VIDEO),
                        ).content
                    }.getOrNull()?.items?.takeIf { it.isNotEmpty() }?.let { items ->
                        rows.add(TvHomeRow("Pokračovat v přehrávání", items.map { it.toTvItem(serverUrl, token) }))
                    }
                }

                if (filter == null || filter == BaseItemKind.MOVIE) {
                    runCatching {
                        apiClient.userLibraryApi.getLatestMedia(
                            userId = userUuid,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                            limit = 20,
                        ).content
                    }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                        rows.add(TvHomeRow("Nejnovější filmy", items.map { it.toTvItem(serverUrl, token) }))
                    }
                }

                if (filter == null || filter == BaseItemKind.SERIES) {
                    runCatching {
                        apiClient.userLibraryApi.getLatestMedia(
                            userId = userUuid,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                            fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                            limit = 20,
                        ).content
                    }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                        rows.add(TvHomeRow("Nejnovější seriály", items.map { it.toTvItem(serverUrl, token) }))
                    }
                }

                _state.update { it.copy(isLoading = false, rows = rows) }
                if (!socketSetup) {
                    socketSetup = true
                    setupPlayMessages()
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba připojení") }
            }
        }
    }

    private fun setupPlayMessages() {
        viewModelScope.launch {
            runCatching {
                apiClient.sessionApi.postCapabilities(
                    playableMediaTypes = listOf(MediaType.VIDEO),
                    supportsMediaControl = true,
                )
            }
        }
        apiClient.webSocket
            .subscribe<PlayMessage>()
            .onEach { msg ->
                val data = msg.data ?: return@onEach
                if (data.playCommand == PlayCommand.PLAY_NOW) {
                    val itemId = data.itemIds?.firstOrNull()?.toString() ?: return@onEach
                    val posMs = (data.startPositionTicks ?: 0L) / 10_000L
                    _playEvents.emit(PlayMessageEvent(itemId, posMs))
                }
            }
            .catch { /* ignore WebSocket errors */ }
            .launchIn(viewModelScope)
    }
}

private fun BaseItemDto.toTvItem(serverUrl: String, token: String) = TvJellyfinItem(
    id = id.toString(),
    name = name ?: "",
    imageUrl = "$serverUrl/Items/$id/Images/Primary?fillWidth=300&quality=90&api_key=$token",
    progressPct = userData?.playedPercentage?.toInt(),
    type = type?.name ?: "",
)
