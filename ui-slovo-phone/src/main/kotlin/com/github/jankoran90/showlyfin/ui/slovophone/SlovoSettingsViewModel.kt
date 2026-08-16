package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.AbsCreds
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
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

/** Stav sekce „Účet / Audioknihy" — přihlášení k Audiobookshelf serveru. */
data class SlovoAccountState(
    val absConfigured: Boolean = false,
    val absBaseUrl: String = "",
    val absLoading: Boolean = false,
    val absError: String? = null,
    /**
     * Adresa serveru zjištěná z JINÉHO profilu (2026-08-16, dva dospělí) — když se přihlašuje Nel
     * poprvé, appka jí předvyplní stejnou adresu serveru jako má Honza (rodinný ABS je jeden), ať
     * nemusí URL opisovat/hádat. null = žádný jiný profil ještě přihlášený není.
     */
    val knownServerUrl: String? = null,
)

/**
 * Slovo (EXCISE/SHW-103, Fáze A; 2026-08-16 rozseknuto na Honza/Nel) — VM sekce Nastavení pro
 * přihlášení ABS. Login zapíše token do kanonických prefs (aby poslech naskočil hned) A ZÁROVEŇ do
 * `ProfileConfig.credentials.abs` PRÁVĚ AKTIVNÍHO profilu přes [ProfileRepository.updateConfig] →
 * cross-device sync per profil (Honza a Nel mají KAŽDÝ SVÉ VLASTNÍ ABS přihlášení a tedy oddělený
 * progres poslechu). Applier drží prefs ⇄ config konzistentní.
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

    init {
        if (!absRepo.isConfigured) {
            viewModelScope.launch {
                val known = profileRepository.getAll()
                    .firstNotNullOfOrNull { ProfileConfig.fromJson(it.configJson).credentials.abs?.url?.takeIf(String::isNotBlank) }
                if (known != null) _account.update { it.copy(knownServerUrl = known) }
            }
        }
    }

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

    /**
     * Odhlášení ABS + smazání creds z balíku profilu (cross-device). Profily (2026-08-16, dva
     * dospělí): cílí JEN na aktivní profil — Honza a Nel mají oddělená přihlášení, odhlášení jednoho
     * se nemá dotknout druhého. Výjimka: Děti nemají vlastní ABS účet (dědí od profilu Honza,
     * `isDefault=true`) — pokud se odhlašuje PRÁVĚ Honza, smaž creds i Dětem, ať appka pro ně
     * neskončí s mrtvým/cizím tokenem (viz [com.github.jankoran90.slovo.SlovoProfileManager]).
     */
    fun absLogout() {
        absRepo.logout()
        viewModelScope.launch {
            val active = profileRepository.activeProfile.value ?: return@launch
            profileRepository.updateConfig(active.id) { c -> c.copy(credentials = c.credentials.copy(abs = null)) }
            if (active.isDefault) {
                profileRepository.getAll().firstOrNull { !it.isAdmin }?.let { kids ->
                    profileRepository.updateConfig(kids.id) { c -> c.copy(credentials = c.credentials.copy(abs = null)) }
                }
            }
            _account.update { it.copy(absConfigured = false, absBaseUrl = "", absError = null) }
        }
    }

    /**
     * Zrcadlí právě uložené ABS creds (z kanonických prefs po [AbsRepository.login]) do balíku
     * AKTIVNÍHO profilu (cross-device). Profily (2026-08-16, dva dospělí): Honza a Nel mají KAŽDÝ
     * SVÉ VLASTNÍ ABS přihlášení — login jednoho nesmí přepsat creds druhého. Výjimka: Děti nemají
     * vlastní ABS účet, dědí od Honzy (`isDefault=true`) — pokud se PRÁVĚ Honza přihlašuje/re-loguje,
     * zrcadli nové creds i jim (viz [com.github.jankoran90.slovo.SlovoProfileManager.ensureKidsProfile]).
     * Token přiložíme, ať se nová instance nemusí re-loginovat; heslo drží re-login na 401 po expiraci.
     */
    private suspend fun persistAbsCredsToProfile() {
        val creds = AbsCreds(
            url = absPrefs.baseUrl,
            username = absPrefs.username,
            password = absPrefs.password,
            token = absPrefs.token.ifBlank { null },
        )
        val active = profileRepository.activeProfile.value
        if (active == null) {
            Timber.w("[SLOVO] persistAbsCreds: žádný aktivní profil — creds jen v prefs (bez cross-device)")
            return
        }
        profileRepository.updateConfig(active.id) { c -> c.copy(credentials = c.credentials.copy(abs = creds)) }
        if (active.isDefault) {
            profileRepository.getAll().firstOrNull { !it.isAdmin }?.let { kids ->
                profileRepository.updateConfig(kids.id) { c -> c.copy(credentials = c.credentials.copy(abs = creds)) }
            }
        }
    }
}
