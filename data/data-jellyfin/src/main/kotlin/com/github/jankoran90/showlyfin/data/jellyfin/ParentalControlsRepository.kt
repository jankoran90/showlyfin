package com.github.jankoran90.showlyfin.data.jellyfin

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    /**
     * COUCH (SHW-88) — číselný věkový strop obsahu (roky) pro OBJEVOVACÍ plochy dětského profilu.
     * Nejpřísnější z: explicitní volby (Nastavení, per-profil) / Jellyfin `maxParentalRating` /
     * profilového override. null = bez omezení (dospělý). Viz [ContentAgeGate].
     */
    val effectiveAgeCap: Int? = null,
    /** COUCH (SHW-88) — přísný režim: skrýt i položky BEZ certifikace (jen když je [effectiveAgeCap] aktivní). */
    val hideUnratedForAge: Boolean = false,
)

@Singleton
class ParentalControlsRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private val _profile = MutableStateFlow(UserProfile(null, AgeRating.UNRESTRICTED, false))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob())

    // COUCH (SHW-88) — aktivní profil pro per-profil explicitní věkový strop.
    @Volatile private var activeProfileId: Long? = null
    @Volatile private var activeOverride: AgeRating? = null

    companion object {
        private const val KEY_USER_ID = "jellyfin_user_id"
        private const val KEY_USER_NAME = "jellyfin_user_name"
        private const val KEY_MAX_PARENTAL_RATING = "jellyfin_max_parental_rating"
        private const val KEY_IS_ADMIN = "jellyfin_is_admin"

        /** Klíč explicitního věkového stropu per profil (roky; 0 = vypnuto). */
        fun ageCapKey(profileId: Long) = "p${profileId}_content_age_cap"

        /** Klíč přísného režimu (skrýt neohodnocené) per profil. */
        fun hideUnratedKey(profileId: Long) = "p${profileId}_content_hide_unrated"
    }

    init {
        loadFromPrefs()
        profileRepository.activeProfile
            .onEach { active ->
                val override = active?.maxAgeRating?.let { runCatching { AgeRating.valueOf(it) }.getOrNull() }
                activeProfileId = active?.id
                activeOverride = override
                applyProfileOverride(override, isAdmin = active?.isAdmin == true)
            }
            .launchIn(scope)
    }

    private fun loadFromPrefs() {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return
        val userName = prefs.getString(KEY_USER_NAME, "") ?: ""
        val maxRating = prefs.getInt(KEY_MAX_PARENTAL_RATING, -1).takeIf { it >= 0 }
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        val info = JellyfinUserInfo(userId, userName, maxRating, isAdmin)
        val ageRating = AgeRating.fromJellyfinMaxParentalRating(maxRating)
        val isLocked = maxRating != null && !isAdmin
        val cap = computeAgeCap(readExplicitCap(), maxRating, activeOverride)
        _profile.value = UserProfile(info, ageRating, isLocked, effectiveAgeCap = cap, hideUnratedForAge = readHideUnrated())
    }

    private fun applyProfileOverride(override: AgeRating?, isAdmin: Boolean) {
        val current = _profile.value
        val info = current.userInfo
        val jellyfinRating = AgeRating.fromJellyfinMaxParentalRating(info?.maxParentalRating)
        val effective = pickStricter(override, jellyfinRating)
        val isLocked = (override != null || info?.maxParentalRating != null) && !isAdmin
        val cap = computeAgeCap(readExplicitCap(), info?.maxParentalRating, override)
        _profile.value = current.copy(
            effectiveAgeRating = effective, isLocked = isLocked,
            effectiveAgeCap = cap, hideUnratedForAge = readHideUnrated(),
        )
    }

    private fun pickStricter(a: AgeRating?, b: AgeRating): AgeRating {
        if (a == null) return b
        return if (a.maxParentalRatingThreshold <= b.maxParentalRatingThreshold) a else b
    }

    // ── COUCH (SHW-88) věkový strop obsahu ──────────────────────────────────────
    /** Explicitní per-profil strop (Nastavení). 0/absent = nenastaveno → null. */
    private fun readExplicitCap(): Int? =
        activeProfileId?.let { id -> prefs.getInt(ageCapKey(id), 0).takeIf { it in 1..17 } }

    /** Nejpřísnější (nejnižší) z platných zdrojů; null = bez omezení (>=18 se ignoruje). */
    private fun computeAgeCap(explicit: Int?, jellyfin: Int?, override: AgeRating?): Int? {
        val overrideCap = override?.maxParentalRatingThreshold?.takeIf { it in 1..17 }
        return listOfNotNull(
            explicit?.takeIf { it in 1..17 },
            jellyfin?.takeIf { it in 1..17 },
            overrideCap,
        ).minOrNull()
    }

    private fun readHideUnrated(): Boolean =
        activeProfileId?.let { prefs.getBoolean(hideUnratedKey(it), false) } ?: false

    /** Aktuálně uložený explicitní strop pro aktivní profil (0 = vypnuto) — pro UI selektor. */
    fun explicitAgeCap(): Int = activeProfileId?.let { prefs.getInt(ageCapKey(it), 0) } ?: 0

    /** Aktuální stav přísného režimu (skrýt neohodnocené) pro aktivní profil — pro UI. */
    fun hideUnrated(): Boolean = readHideUnrated()

    /** Nastav explicitní věkový strop pro aktivní profil (roky; 0 = vypnuto) + přepočti. */
    fun setContentAgeCap(capYears: Int) {
        val id = activeProfileId ?: return
        prefs.edit().putInt(ageCapKey(id), capYears).apply()
        applyProfileOverride(activeOverride, isAdmin = _profile.value.userInfo?.isAdministrator == true)
    }

    /** Zapni/vypni přísný režim (skrýt neohodnocené) pro aktivní profil + přepočti. */
    fun setHideUnrated(enabled: Boolean) {
        val id = activeProfileId ?: return
        prefs.edit().putBoolean(hideUnratedKey(id), enabled).apply()
        applyProfileOverride(activeOverride, isAdmin = _profile.value.userInfo?.isAdministrator == true)
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
