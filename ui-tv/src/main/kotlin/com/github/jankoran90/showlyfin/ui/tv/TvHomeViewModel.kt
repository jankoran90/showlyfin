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
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
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
    private val tvPreferences: TvPreferences,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(TvHomeUiState())
    val state: StateFlow<TvHomeUiState> = _state.asStateFlow()

    private val _playEvents = MutableSharedFlow<PlayMessageEvent>(extraBufferCapacity = 1)
    val playEvents: SharedFlow<PlayMessageEvent> = _playEvents.asSharedFlow()

    private var socketSetup = false

    private var lastRowPrefsKey: String? = null

    init {
        tvPreferences.state
            .onEach { p ->
                _state.update {
                    it.copy(
                        cardSize = p.cardSize,
                        pinnedLibraries = p.pinnedLibraries,
                        drawerOrder = p.drawerOrder,
                    )
                }
                val key = "${p.showResumeRow}|${p.showNextUp}|${p.showRecentlyAdded}|" +
                    "${p.rowItemLimit}|${p.enabledLibraryIds.sorted()}"
                if (lastRowPrefsKey != null && lastRowPrefsKey != key) loadItems()
                lastRowPrefsKey = key
            }
            .launchIn(viewModelScope)
        loadItems()
    }

    fun setFilter(filter: BaseItemKind?) {
        _state.update { it.copy(filter = filter) }
        loadItems()
    }

    fun reload() = loadItems()

    fun setDrawerOrder(order: List<String>) = tvPreferences.setDrawerOrder(order)

    /** Přesun řady na Home nahoru/dolů (move mode) — persist + okamžitě přeskládá state. */
    fun moveRow(key: String, up: Boolean) {
        val current = _state.value.rows
        val idx = current.indexOfFirst { it.key == key }
        val target = if (up) idx - 1 else idx + 1
        if (idx < 0 || target !in current.indices) return
        val reordered = current.toMutableList().apply { add(target, removeAt(idx)) }
        tvPreferences.setRowOrder(reordered.map { it.key })
        _state.update { it.copy(rows = reordered) }
    }

    /** Seřadí řady dle uloženého rowOrder; neznámé klíče (nové řady) zachová na původním místě na konci. */
    private fun orderRows(rows: List<TvHomeRow>): List<TvHomeRow> {
        val order = tvPreferences.state.value.rowOrder
        if (order.isEmpty()) return rows
        val byKey = rows.associateBy { it.key }
        val ordered = order.mapNotNull { byKey[it] }
        val rest = rows.filter { it.key !in order }
        return ordered + rest
    }

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
                val display = tvPreferences.state.value
                val fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

                val rowLimit = display.rowItemLimit

                // Resume (Continue Watching) — only on unfiltered Home, toggle controlled
                if (filter == null && display.showResumeRow) {
                    runCatching {
                        apiClient.itemsApi.getResumeItems(
                            userId = userUuid,
                            limit = rowLimit,
                            fields = fields,
                            mediaTypes = listOf(MediaType.VIDEO),
                            enableTotalRecordCount = false,
                        ).content.items
                    }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                        rows.add(TvHomeRow("Pokračovat v přehrávání", items.map { it.toTvItem(serverUrl, token) }, key = "resume"))
                    }
                }

                // Next Up (series) — toggle controlled
                if (display.showNextUp && (filter == null || filter == BaseItemKind.SERIES)) {
                    runCatching {
                        apiClient.tvShowsApi.getNextUp(
                            userId = userUuid,
                            limit = rowLimit,
                            fields = fields,
                            enableTotalRecordCount = false,
                        ).content.items
                    }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                        rows.add(TvHomeRow("Pokračovat v seriálech", items.map { it.toTvItem(serverUrl, token) }, key = "nextup"))
                    }
                }

                // Per-library "Recently added" rows — dynamic, respects enabled libraries + filter
                if (display.showRecentlyAdded) {
                    val views = runCatching {
                        apiClient.userViewsApi.getUserViews(userId = userUuid).content.items
                    }.getOrNull().orEmpty()
                    for (view in views) {
                        val viewId = view.id.toString()
                        if (!tvPreferences.isLibraryEnabled(viewId)) continue
                        val ct = view.collectionType?.name?.lowercase()
                        // skip non-video libraries (music, books, photos)
                        if (ct != null && ct !in VIDEO_COLLECTION_TYPES) continue
                        // nav-drawer filter: Movies/Series narrows to matching libraries
                        if (filter == BaseItemKind.MOVIE && ct != null && ct != "movies" && ct != "boxsets") continue
                        if (filter == BaseItemKind.SERIES && ct != null && ct != "tvshows") continue
                        runCatching {
                            apiClient.userLibraryApi.getLatestMedia(
                                userId = userUuid,
                                parentId = view.id,
                                fields = fields,
                                limit = rowLimit,
                            ).content
                        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                            rows.add(
                                TvHomeRow(
                                    title = "Nejnovější: ${view.name}",
                                    items = items.map { it.toTvItem(serverUrl, token) },
                                    key = "lib:$viewId",
                                    libraryId = viewId,
                                    libraryName = view.name ?: "",
                                    collectionType = view.collectionType?.name,
                                ),
                            )
                        }
                    }
                }

                _state.update { it.copy(isLoading = false, rows = orderRows(rows)) }
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

private val VIDEO_COLLECTION_TYPES = setOf("movies", "tvshows", "boxsets", "homevideos", "mixed")

private fun BaseItemDto.toTvItem(serverUrl: String, token: String): TvJellyfinItem {
    // backdrop: vlastní, nebo z rodiče (epizoda → seriál); fallback řeší UI
    val backdropItemId = when {
        !backdropImageTags.isNullOrEmpty() -> id
        parentBackdropItemId != null -> parentBackdropItemId
        else -> null
    }
    return TvJellyfinItem(
        id = id.toString(),
        name = name ?: "",
        imageUrl = "$serverUrl/Items/$id/Images/Primary?fillWidth=300&quality=90&api_key=$token",
        progressPct = userData?.playedPercentage?.toInt(),
        type = type?.name ?: "",
        backdropUrl = backdropItemId?.let {
            "$serverUrl/Items/$it/Images/Backdrop/0?fillWidth=1280&quality=80&api_key=$token"
        },
    )
}
