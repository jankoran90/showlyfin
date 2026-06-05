package com.github.jankoran90.showlyfin.data.jellyfin

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class UserProfile(
    val userInfo: JellyfinUserInfo?,
    val effectiveAgeRating: AgeRating,
    val isLocked: Boolean,
)

@Singleton
class ParentalControlsRepository @Inject constructor(
    private val apiClient: ApiClient,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private val _profile = MutableStateFlow(UserProfile(null, AgeRating.UNRESTRICTED, false))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    companion object {
        private const val KEY_USER_ID = "jellyfin_user_id"
        private const val KEY_USER_NAME = "jellyfin_user_name"
        private const val KEY_MAX_PARENTAL_RATING = "jellyfin_max_parental_rating"
        private const val KEY_IS_ADMIN = "jellyfin_is_admin"
    }

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return
        val userName = prefs.getString(KEY_USER_NAME, "") ?: ""
        val maxRating = prefs.getInt(KEY_MAX_PARENTAL_RATING, -1).takeIf { it >= 0 }
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        val info = JellyfinUserInfo(userId, userName, maxRating, isAdmin)
        val ageRating = AgeRating.fromJellyfinMaxParentalRating(maxRating)
        val isLocked = maxRating != null && !isAdmin
        _profile.value = UserProfile(info, ageRating, isLocked)
    }

    suspend fun refreshFromJellyfin(userId: String) {
        runCatching {
            val userUuid = UUID.fromString(userId)
            val user = apiClient.userApi.getUserById(userId = userUuid).content
            val maxRating = user.policy?.maxParentalRating
            val isAdmin = user.policy?.isAdministrator ?: false
            val info = JellyfinUserInfo(
                userId = userId,
                userName = user.name ?: "",
                maxParentalRating = maxRating,
                isAdministrator = isAdmin,
            )
            prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_NAME, info.userName)
                .putInt(KEY_MAX_PARENTAL_RATING, maxRating ?: -1)
                .putBoolean(KEY_IS_ADMIN, isAdmin)
                .apply()
            val ageRating = AgeRating.fromJellyfinMaxParentalRating(maxRating)
            val isLocked = maxRating != null && !isAdmin
            _profile.value = UserProfile(info, ageRating, isLocked)
        }
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_MAX_PARENTAL_RATING)
            .remove(KEY_IS_ADMIN)
            .apply()
        _profile.value = UserProfile(null, AgeRating.UNRESTRICTED, false)
    }
}
