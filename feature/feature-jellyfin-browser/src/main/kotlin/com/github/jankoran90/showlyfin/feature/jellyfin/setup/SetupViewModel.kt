package com.github.jankoran90.showlyfin.feature.jellyfin.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.JellyfinCreds
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject

data class PublicUserInfo(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val hasPassword: Boolean,
)

enum class SetupStage { URL, USERS, PASSWORD, DONE }

data class SetupUiState(
    val stage: SetupStage = SetupStage.URL,
    val serverUrl: String = "",
    val users: List<PublicUserInfo> = emptyList(),
    val selectedUser: PublicUserInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun fetchUsers(rawUrl: String) {
        val serverUrl = normalizeUrl(rawUrl)
        if (serverUrl.isBlank()) {
            _uiState.update { it.copy(error = "Zadej URL serveru") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, serverUrl = serverUrl) }
            try {
                val tempApi = jellyfin.createApi(baseUrl = serverUrl)
                val response by tempApi.userApi.getPublicUsers()
                val users = response.map { dto ->
                    val id = dto.id.toString()
                    val tag = dto.primaryImageTag
                    val avatarUrl = tag?.let { "$serverUrl/Users/$id/Images/Primary?tag=$it&quality=85" }
                    PublicUserInfo(
                        id = id,
                        name = dto.name ?: "?",
                        avatarUrl = avatarUrl,
                        hasPassword = dto.hasPassword == true,
                    )
                }
                Timber.i("[Setup] fetched ${users.size} public users from $serverUrl")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        stage = if (users.isEmpty()) SetupStage.PASSWORD else SetupStage.USERS,
                        users = users,
                    )
                }
            } catch (e: Throwable) {
                Timber.w(e, "[Setup] fetchPublicUsers failed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Nepodařilo se načíst seznam uživatelů: ${e.message}. Zkus zadat jméno ručně.",
                        stage = SetupStage.PASSWORD,
                    )
                }
            }
        }
    }

    fun selectUser(user: PublicUserInfo) {
        _uiState.update { it.copy(selectedUser = user, stage = SetupStage.PASSWORD, error = null) }
    }

    fun backToUsers() {
        _uiState.update {
            it.copy(stage = if (it.users.isNotEmpty()) SetupStage.USERS else SetupStage.URL, error = null)
        }
    }

    fun backToUrl() {
        _uiState.update { SetupUiState() }
    }

    fun authenticate(username: String, password: String, rememberPassword: Boolean = true) {
        val serverUrl = _uiState.value.serverUrl
        if (serverUrl.isBlank()) {
            _uiState.update { it.copy(error = "Server URL chybí") }
            return
        }
        if (username.isBlank()) {
            _uiState.update { it.copy(error = "Zadej uživatelské jméno") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val tempApi = jellyfin.createApi(baseUrl = serverUrl)
                val authResult by tempApi.userApi.authenticateUserByName(
                    username = username,
                    password = password,
                )
                val accessToken = authResult.accessToken
                    ?: throw IllegalStateException("Server nevrátil přístupový token")
                val userId = authResult.user?.id?.toString()
                    ?: throw IllegalStateException("Server nevrátil ID uživatele")
                apiClient.update(
                    baseUrl = serverUrl,
                    accessToken = accessToken,
                    clientInfo = clientInfo,
                    deviceInfo = deviceInfo,
                )
                parentalControlsRepository.refreshFromJellyfin(userId)
                val isAdmin = runCatching {
                    apiClient.userApi.getUserById(userId = UUID.fromString(userId))
                        .content.policy?.isAdministrator ?: false
                }.getOrDefault(false)
                val existing = profileRepository.getAll().firstOrNull {
                    it.serverUrl == serverUrl && it.jellyfinUserId == userId
                }
                // Plan PROFILES 1D: do balíku ulož Jellyfin creds (heslo jen když „zapamatovat heslo").
                // Zachovej existující config balík profilu (žánry/sekce/restrikce) — jen přepiš creds.
                val baseConfig = ProfileConfig.fromJson(existing?.configJson)
                val mergedConfig = baseConfig.copy(
                    credentials = baseConfig.credentials.copy(
                        jellyfin = JellyfinCreds(
                            url = serverUrl,
                            userId = userId,
                            token = accessToken,
                            username = authResult.user?.name ?: username,
                            password = if (rememberPassword) password else null,
                        ),
                    ),
                )
                val profile = ProfileEntity(
                    id = existing?.id ?: 0L,
                    name = existing?.name ?: authResult.user?.name ?: username,
                    serverUrl = serverUrl,
                    jellyfinUserId = userId,
                    jellyfinToken = accessToken,
                    avatarTag = authResult.user?.primaryImageTag,
                    isAdmin = isAdmin,
                    isDefault = existing?.isDefault ?: (profileRepository.count() == 0),
                    tvDefault = existing?.tvDefault ?: false,
                    maxAgeRating = existing?.maxAgeRating,
                    configJson = ProfileConfig.toJson(mergedConfig),
                    avatarPath = existing?.avatarPath,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
                val savedId = profileRepository.upsert(profile)
                profileRepository.setActive(savedId)
                Timber.i("[Setup] authenticated '${profile.name}' admin=$isAdmin profileId=$savedId")
                _uiState.update { it.copy(isLoading = false, stage = SetupStage.DONE) }
            } catch (e: Throwable) {
                Timber.w(e, "[Setup] authenticate failed")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba přihlášení") }
            }
        }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://$trimmed"
        }
        return trimmed
    }
}
