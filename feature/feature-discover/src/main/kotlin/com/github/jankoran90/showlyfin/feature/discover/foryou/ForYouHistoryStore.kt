package com.github.jankoran90.showlyfin.feature.discover.foryou

import android.content.Context
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SPOTLIGHT+ (user 2026-08-27: „ale ukladejme historii") — archiv dřívějších dávek doporučení
 * „Pro tebe — po kategoriích".
 *
 * Kategorie se dosud počítaly při každém otevření znovu a starý výběr se ztratil. Když si teď divák
 * vyžádá čerstvý výběr (Vybrat znovu), nesmí mu předchozí zmizet — uloží se sem a zobrazí se pod
 * čerstvou dávkou. Přežije zavření appky (na rozdíl od dosavadního stavu jen v paměti).
 *
 * Per profil, device-local. Držíme posledních [MAX_BATCHES] dávek — je to archiv k prolistování,
 * ne trvalý sklad; bez stropu by soubor rostl donekonečna.
 */
@Singleton
class ForYouHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val profileRepository: ProfileRepository,
    private val gson: Gson,
) {

    /** Jedna uložená dávka = co kurátor nabídl v jeden okamžik. */
    data class Batch(
        val createdAtMs: Long = 0L,
        val rails: List<CuratorRail> = emptyList(),
    )

    private val prefs = context.getSharedPreferences("curator_foryou_history", Context.MODE_PRIVATE)

    private fun key(): String =
        "history_" + (profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() } ?: "anon")

    fun load(): List<Batch> = runCatching {
        val raw = prefs.getString(key(), null) ?: return emptyList()
        val type = object : TypeToken<List<Batch>>() {}.type
        gson.fromJson<List<Batch>>(raw, type).orEmpty()
    }.onFailure { Timber.w(it, "[FORYOU] načtení historie selhalo") }.getOrDefault(emptyList())

    /** Ulož dávku navrch. Prázdnou neukládáme — prázdný archiv nikomu nepomůže. */
    fun push(rails: List<CuratorRail>) {
        val usable = rails.filter { it.items.isNotEmpty() }
        if (usable.isEmpty()) return
        val next = (listOf(Batch(System.currentTimeMillis(), usable)) + load()).take(MAX_BATCHES)
        runCatching { prefs.edit().putString(key(), gson.toJson(next)).apply() }
            .onFailure { Timber.w(it, "[FORYOU] uložení historie selhalo") }
    }

    fun clear() {
        runCatching { prefs.edit().remove(key()).apply() }
            .onFailure { Timber.w(it, "[FORYOU] smazání historie selhalo") }
    }

    private companion object {
        const val MAX_BATCHES = 4
    }
}
