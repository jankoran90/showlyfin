package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Záplata configu profilu na server — převzato z Filmů (`ConfigPendingPatch`, incident 2026-10-14), ve Slovu od
 * 2026-11-22 (user „vyřešte to společně pořádně, vše musí být pevné"): Slovo posílalo CELÝ místní config. Profil
 * Honza sdílí serverový záznam s Filmy (Dospělý) → zastaralá místní kopie polí Filmů (titulky, zvuk, kurátor…)
 * přepsala jejich novější hodnoty a Filmy stejně tak mohly vrátit pořady skryté ve Slovu.
 *
 * Na server jde jen ZÁPLATA — top-level klíče, které konkrétní úprava změnila (starý → nový kanonický JSON,
 * `ProfileConfig.toJson`). Server chybějící klíče ponechá (`_put_config`), takže na pole, na která appka nesáhla,
 * už nic nepřepíše. Neodeslaná záplata čeká v prefs a přibírá další změny, dokud neprojde (nahrazuje dřívější
 * příznak „neodesláno" + celý config). Kanonická podoba (výchozí hodnoty doplněné) je nutná: proti pahýlu „{}"
 * by jinak vypadalo jako změněné každé pole (Filmy, incident 2026-10-14 večer).
 *
 * Proti Filmům jen jiný JSON parser (kotlinx místo org.json — kvůli JVM unit testům); chování stejné.
 */
// Čti-uprav-zapiš nad prefs je pod zámkem instance: záplatu doplňují i cesty mimo configEditMutex
// (Trakt token ze setActive, přiřazení šablony) — souběžný zápis by jinak zahodil klíče druhého (revize 2026-11-22).
internal class ConfigPendingPatch(private val prefs: SharedPreferences) {

    /** Přidá do čekající záplaty klíče, které se mezi [oldJson] a [newJson] změnily. */
    fun add(key: String, oldJson: String?, newJson: String) = synchronized(this) {
        val merged = mergePatch(get(key), oldJson, newJson) ?: return@synchronized
        prefs.edit().putString(PREFIX + key, merged).apply()
    }

    /**
     * Přidá do záplaty úpravu, která se dřív uložila jen místně ([PendingConfigEdits]: [baseJson] → [mineJson]) a po
     * stažení serverové verze je přenesená v místní kopii [canonical]. Klíče (u credentials jen služby), které ta
     * úprava změnila, se berou s hodnotou z [canonical] (výsledek tříbodového sloučení se serverem).
     */
    fun addEdit(key: String, canonical: String, baseJson: String, mineJson: String) = synchronized(this) {
        val changed = parseObject(mergePatch(null, baseJson, mineJson)) ?: return@synchronized
        val src = parseObject(canonical) ?: return@synchronized
        val patch = LinkedHashMap(parseObject(get(key)).orEmpty())
        for ((k, v) in changed) {
            val cur = src[k] ?: continue
            patch[k] = if (k in PER_SERVICE_KEYS && v is JsonObject && cur is JsonObject) {
                val acc = LinkedHashMap((patch[k] as? JsonObject).orEmpty())
                for (s in v.keys) acc[s] = cur[s] ?: JsonNull
                JsonObject(acc)
            } else cur
        }
        if (patch.isNotEmpty()) prefs.edit().putString(PREFIX + key, JsonObject(patch).toString()).apply()
    }

    /** Zahoď čekající záplatu. */
    fun clear(key: String) = synchronized(this) {
        if (prefs.contains(PREFIX + key)) prefs.edit().remove(PREFIX + key).apply()
    }

    fun get(key: String): String? = prefs.getString(PREFIX + key, null)?.takeIf { it.isNotBlank() && it != "{}" }

    /** Odeslaná záplata = hotovo; jen pokud mezitím nepřibyla další změna (ta počká na příští kolo). */
    fun clearIfSame(key: String, sent: String) = synchronized(this) {
        if (get(key) == sent) prefs.edit().remove(PREFIX + key).apply()
    }

    private companion object {
        const val PREFIX = "profile_config_patch_"
    }
}

private val patchJson = Json { ignoreUnknownKeys = true }

private fun parseObject(raw: String?): JsonObject? =
    raw?.let { runCatching { patchJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }

/**
 * Čistá logika záplaty (testovatelná bez prefs): k [existing] přidá klíče, které se mezi [oldJson] a [newJson]
 * změnily (nebo v [oldJson] chyběly). Vrací novou záplatu, nebo null, když není co uložit.
 * Klíč, který z [newJson] zmizel, se do záplaty nedává — chybějící klíč server ponechá.
 */
internal fun mergePatch(existing: String?, oldJson: String?, newJson: String): String? {
    val new = parseObject(newJson) ?: return null
    val old = parseObject(oldJson) ?: JsonObject(emptyMap())
    val patch = LinkedHashMap(parseObject(existing).orEmpty())
    for ((k, v) in new) {
        if (old[k] == v) continue
        val oldObj = old[k] as? JsonObject
        val newObj = v as? JsonObject
        patch[k] = if (k in PER_SERVICE_KEYS && oldObj != null && newObj != null) {
            // Jen změněné služby (null = výslovné odhlášení) — server je od 2026-11-22 slučuje po službách
            // (jellyfin-uploader 920404a); celé credentials by vrátily starou kopii ostatních služeb (revize).
            val acc = LinkedHashMap((patch[k] as? JsonObject).orEmpty())
            for ((s, sv) in newObj) if (oldObj[s] != sv) acc[s] = sv
            for (s in oldObj.keys - newObj.keys) acc[s] = JsonNull
            JsonObject(acc)
        } else v
    }
    return if (patch.isEmpty()) null else JsonObject(patch).toString()
}

/** Klíče, které server slučuje po položkách (služby přihlášení) — záplata nese jen změněné položky. */
private val PER_SERVICE_KEYS = setOf("credentials")
