package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Úpravy configu NEAKTIVNÍHO profilu, které nešlo postavit na ověřené serverové verzi (offline, výpadek
 * serveru) — 2026-11-19, Slovo g32. Celou místní kopii nesmíme odeslat (může být týdny stará / pahýl z čerstvé
 * instalace a přepsala by server), ale úpravu nesmíme ani zahodit (schválení pořadu dětem by tiše zmizelo).
 * Proto si pamatujeme ROZDÍL: výchozí stav (base) před první takovou úpravou a poslední výsledek (mine). Jakmile
 * se serverová verze podaří stáhnout, rozdíl se na ni přenese tříbodovým sloučením ([threeWayMerge]).
 */
internal class PendingConfigEdits(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefKey(key: String) = "profile_config_pending_$key"

    fun has(key: String): Boolean = prefs.contains(prefKey(key))

    /** Zaznamená úpravu: base zůstává z PRVNÍ nepotvrzené úpravy, mine se přepíše posledním stavem. */
    fun record(key: String, baseJson: String, mineJson: String) {
        val existingBase = read(key)?.get("base")
        val entry = buildJsonObject {
            put("base", existingBase ?: parse(baseJson))
            put("mine", parse(mineJson))
        }
        prefs.edit().putString(prefKey(key), entry.toString()).apply()
    }

    /** Přenese zaznamenaný rozdíl na čerstvou serverovou verzi; bez záznamu vrátí [remoteJson] beze změny. */
    fun applyOnto(key: String, remoteJson: String): String {
        val entry = read(key) ?: return remoteJson
        val merged = threeWayMerge(entry["base"], entry["mine"], parse(remoteJson)) ?: return remoteJson
        return merged.toString()
    }

    fun clear(key: String) {
        if (has(key)) prefs.edit().remove(prefKey(key)).apply()
    }

    private fun read(key: String): JsonObject? =
        prefs.getString(prefKey(key), null)?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private fun parse(raw: String): JsonElement =
        runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }) }.getOrDefault(JsonObject(emptyMap()))
}

/**
 * Tříbodové sloučení: co [mine] oproti [base] nezměnil, bere se ze serveru ([theirs]). Objekty se slučují po
 * klíčích, seznamy jako množiny (přidané položky se přidají, odebrané odeberou, serverové ostatní zůstanou),
 * jinak vyhrává [mine]. null = klíč chybí.
 */
internal fun threeWayMerge(base: JsonElement?, mine: JsonElement?, theirs: JsonElement?): JsonElement? {
    if (mine == base) return theirs
    if (mine is JsonObject && theirs is JsonObject) {
        val b = base as? JsonObject ?: JsonObject(emptyMap())
        val keys = mine.keys + theirs.keys + b.keys
        return JsonObject(keys.mapNotNull { k -> threeWayMerge(b[k], mine[k], theirs[k])?.let { k to it } }.toMap())
    }
    if (mine is JsonArray && theirs is JsonArray) {
        val b = (base as? JsonArray)?.toList().orEmpty()
        val added = mine.filter { it !in b }
        val removed = b.filter { it !in mine }
        return JsonArray(theirs.filter { it !in removed } + added.filter { it !in theirs })
    }
    return mine
}
