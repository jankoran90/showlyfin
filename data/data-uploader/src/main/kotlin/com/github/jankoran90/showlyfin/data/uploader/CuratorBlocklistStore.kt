package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SPOTLIGHT+ (user 2026-08-27: „Tohle mi nenabízej") — tituly, které kurátor nesmí doporučovat.
 *
 * Do dneška se filtrovalo JEN to, co divák viděl (`knownIds`) a co špatně ohodnotil na Traktu —
 * na film, který prostě nezajímá, neexistovala páka. Blokace jde dvěma cestami zároveň:
 *  1. **do promptu** (`avoid`) — mozek to nemá vůbec navrhovat, ať se na to nespotřebují sloty;
 *  2. **filtrem výsledku** — kdyby to navrhl přesto, do UI se to nedostane.
 *
 * Per profil (dětský a dospělý mají vlastní), device-local. Blokace se NEMAŽE, jen se dá zrušit
 * v Nastavení — user 2026-08-27 („ale ukládejme historii"): omylem zablokovaný film musí jít vrátit.
 *
 * VLASTNÍ SharedPreferences (`curator_blocklist`), ne sdílené `trakt_prefs` — ty maže odhlášení
 * Traktu a černá listina s Traktem nesouvisí (týž důvod jako u [ViewModeStore]).
 */
@Singleton
class CuratorBlocklistStore @Inject constructor(
    @ApplicationContext context: Context,
    private val profileRepository: ProfileRepository,
    private val gson: Gson,
) {

    /** Zablokovaný titul. [blockedAtMs] drží pořadí „naposledy zablokované navrchu" v Nastavení. */
    data class Blocked(
        val tmdbId: Long,
        val title: String = "",
        val year: Int? = null,
        val blockedAtMs: Long = 0L,
    )

    private val prefs = context.getSharedPreferences("curator_blocklist", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Blocked>> = _items.asStateFlow()

    /** Profil bez identity (nepřihlášeno) → jeden společný kbelík; jinak per profil. */
    private fun key(): String =
        "blocked_" + (profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() } ?: "anon")

    private fun load(): List<Blocked> = runCatching {
        val raw = prefs.getString(key(), null) ?: return emptyList()
        val type = object : TypeToken<List<Blocked>>() {}.type
        gson.fromJson<List<Blocked>>(raw, type).orEmpty()
    }.onFailure { Timber.w(it, "[BLOCKLIST] načtení selhalo") }.getOrDefault(emptyList())

    private fun persist(list: List<Blocked>) {
        runCatching { prefs.edit().putString(key(), gson.toJson(list)).apply() }
            .onFailure { Timber.w(it, "[BLOCKLIST] uložení selhalo") }
        _items.value = list
    }

    /** Znovu načti seznam aktivního profilu (volá se po přepnutí profilu). */
    fun reload() { _items.value = load() }

    fun isBlocked(tmdbId: Long?): Boolean =
        tmdbId != null && _items.value.any { it.tmdbId == tmdbId }

    fun blockedIds(): Set<Long> = _items.value.map { it.tmdbId }.toSet()

    /** Názvy pro prompt mozku (`avoid`) — „Titul (rok)", ať si model nesplete jmenovce. */
    fun blockedTitles(): List<String> = _items.value.mapNotNull { b ->
        b.title.takeIf { it.isNotBlank() }?.let { t -> if (b.year != null) "$t (${b.year})" else t }
    }

    fun block(tmdbId: Long, title: String, year: Int?) {
        if (tmdbId <= 0L) return
        if (isBlocked(tmdbId)) return
        persist(_items.value + Blocked(tmdbId, title, year, System.currentTimeMillis()))
    }

    fun unblock(tmdbId: Long) {
        persist(_items.value.filterNot { it.tmdbId == tmdbId })
    }
}
