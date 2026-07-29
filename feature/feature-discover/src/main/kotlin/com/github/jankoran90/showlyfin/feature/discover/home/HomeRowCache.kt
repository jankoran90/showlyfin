package com.github.jankoran90.showlyfin.feature.discover.home

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Poslední známý obsah řad domova na disku — aby appka po startu ukázala obsah HNED, místo aby
 * čekala na síť (user 2026-07-29: „obecně bych radši, kdyby se obsah appky na home, filmotéka
 * a ostatní načetly do vteřiny").
 *
 * Vzor „ukaž staré, na pozadí obnov": [HomeRowCache.read] vykreslí to, co bylo naposledy vidět,
 * a jakmile dorazí čerstvá data ze sítě, řada se tiše přepíše. Cache je **per profil** (klíč nese
 * `profileKey`) — jinak by dětský profil po startu bliknul obsahem dospělého.
 *
 * Zapisujeme jen NEPRÁZDNÉ výsledky: prázdná řada bývá chyba nebo timeout, a tou si nechceme
 * přepsat použitelný obsah.
 */
@Singleton
class HomeRowCache @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_row_cache", Context.MODE_PRIVATE)

    private val type = object : TypeToken<List<HomeRowItem>>() {}.type

    /** Poslední známý obsah řady, nebo null (nic uloženo / cizí profil / prošlé / nečitelné). */
    fun read(profileKey: String, rowId: String): List<HomeRowItem>? {
        if (profileKey.isBlank()) return null
        val raw = prefs.getString(key(profileKey, rowId), null) ?: return null
        val stamp = prefs.getLong(stampKey(profileKey, rowId), 0L)
        if (stamp > 0 && System.currentTimeMillis() - stamp > MAX_AGE_MS) return null
        return runCatching { gson.fromJson<List<HomeRowItem>>(raw, type) }
            .onFailure { Timber.w(it, "[HomeCache] nečitelná cache řady %s", rowId) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun write(profileKey: String, rowId: String, items: List<HomeRowItem>) {
        if (profileKey.isBlank() || items.isEmpty()) return
        runCatching {
            prefs.edit()
                .putString(key(profileKey, rowId), gson.toJson(items.take(MAX_ITEMS), type))
                .putLong(stampKey(profileKey, rowId), System.currentTimeMillis())
                .apply()
        }.onFailure { Timber.w(it, "[HomeCache] zápis řady %s selhal", rowId) }
    }

    /** Zahoď vše (odhlášení / reset). Přepnutí profilu čistit netřeba — klíč nese profil. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun key(profileKey: String, rowId: String) = "$profileKey|$rowId"
    private fun stampKey(profileKey: String, rowId: String) = "$profileKey|$rowId|at"

    private companion object {
        /** Kolik položek řady si pamatovat (víc se stejně na obrazovku nevejde). */
        const val MAX_ITEMS = 30

        /** Jak staré si smí appka pamatovat, než radši počká na síť. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
