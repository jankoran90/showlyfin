package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.AbsCreds
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Stav sekce „Účet / Audioknihy" — přihlášení k Audiobookshelf serveru (single-user Slovo). */
data class SlovoAccountState(
    val absConfigured: Boolean = false,
    val absBaseUrl: String = "",
    val absLoading: Boolean = false,
    val absError: String? = null,
)

/**
 * Slovo (EXCISE/SHW-103, Fáze A) — VM sekce Nastavení pro přihlášení ABS. Single-user: cílí na aktivní
 * (jediný) profil `slovo-main`. Login zapíše token do kanonických prefs (aby poslech naskočil hned) A
 * ZÁROVEŇ do `ProfileConfig.credentials.abs` přes [ProfileRepository.updateConfig] → cross-device sync
 * pod klíčem `slovo-main` (oddělený od Filmy). Applier drží prefs ⇄ config konzistentní.
 */
@HiltViewModel
class SlovoSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val absRepo: AbsRepository,
    private val absPrefs: AbsPreferences,
) : ViewModel() {

    private val _account = MutableStateFlow(
        SlovoAccountState(absConfigured = absRepo.isConfigured, absBaseUrl = absRepo.baseUrl),
    )
    val account: StateFlow<SlovoAccountState> = _account.asStateFlow()

    /** Přihlášení k ABS serveru + write-through do balíku profilu (cross-device). */
    fun absLogin(url: String, username: String, password: String) {
        _account.update { it.copy(absLoading = true, absError = null) }
        viewModelScope.launch {
            absRepo.login(url, username, password)
                .onSuccess {
                    persistAbsCredsToProfile()
                    _account.update {
                        it.copy(absLoading = false, absConfigured = true, absBaseUrl = absRepo.baseUrl)
                    }
                }
                .onFailure { e ->
                    _account.update { it.copy(absLoading = false, absError = e.message ?: "Přihlášení selhalo") }
                }
        }
    }

    /** Odhlášení ABS + smazání creds z balíku profilu (cross-device). */
    fun absLogout() {
        absRepo.logout()
        viewModelScope.launch {
            profileRepository.activeProfile.value?.id?.let { pid ->
                profileRepository.updateConfig(pid) { c ->
                    c.copy(credentials = c.credentials.copy(abs = null))
                }
            }
            _account.update { it.copy(absConfigured = false, absBaseUrl = "", absError = null) }
        }
    }

    /**
     * Zrcadlí právě uložené ABS creds (z kanonických prefs po [AbsRepository.login]) do balíku profilu →
     * backend push pod `slovo-main` → poslech je přihlášený na dalších zařízeních. Token přiložíme, ať se
     * nová instance nemusí re-loginovat; heslo drží re-login na 401 po expiraci tokenu.
     */
    private suspend fun persistAbsCredsToProfile() {
        val pid = profileRepository.activeProfile.value?.id ?: run {
            Timber.w("[SLOVO] persistAbsCreds: žádný aktivní profil — creds jen v prefs (bez cross-device)")
            return
        }
        val creds = AbsCreds(
            url = absPrefs.baseUrl,
            username = absPrefs.username,
            password = absPrefs.password,
            token = absPrefs.token.ifBlank { null },
        )
        profileRepository.updateConfig(pid) { c ->
            c.copy(credentials = c.credentials.copy(abs = creds))
        }
    }
}
