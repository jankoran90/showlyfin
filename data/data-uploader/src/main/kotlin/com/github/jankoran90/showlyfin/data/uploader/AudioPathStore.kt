package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * SEZONA (SHW-113) f2 — VOLBA ZVUKOVÉ STOPY, **plošně za profil** (user 2026-08-01 16:45: *„ten jazykový
 * chip plošně na celý profil — karty filmu, seriálu, pořadu"*). Chip je dosažitelný z každé karty, ale
 * přepíná nastavení celého profilu, ne jednoho titulu.
 *
 * Výchozí hodnota se NEUKLÁDÁ — plyne z věkového ratingu profilu (user 16:40: *„dospělý profil má
 * výchozí originál stopy a dětský profil výchozí cz stopy"*), takže dokud divák chip nesáhne, profil se
 * chová správně sám od sebe a nový profil nemusí nic nastavovat.
 *
 * 🔴 **Proč je to víc než kosmetika:** u dospělého profilu se má hrát ORIGINÁL, jenže „originál" v souboru
 * označený nebývá — u Breaking Bad byla první v pořadí německá stopa a přehrávač ji spustil, protože žádnou
 * preferenci jazyka nedostal (user 16:44: *„breaking bad není německy seriál"*). Proto se volba překládá
 * až na konkrétní JAZYKY (viz [languagesFor]) podle původního jazyka titulu z TMDB.
 *
 * Úložiště je vlastní soubor — sdílené `trakt_prefs` umí `revokeToken()` celé vyčistit.
 */
@Singleton
class AudioPathStore @Inject constructor(
    @ApplicationContext context: Context,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("audio_path_prefs", Context.MODE_PRIVATE)

    /** Co chce divák slyšet. `CZ` = český dabing, `ORIGINAL` = původní jazyk titulu. */
    enum class Choice { CZ, ORIGINAL }

    private fun profileKey(): String = appPrefs.getString("jellyfin_user_id", "").orEmpty().ifBlank { "default" }

    private fun key() = "audio_choice_${profileKey()}"

    private val _choice = MutableStateFlow(readStored())
    /** Uložená volba profilu; null = řídí se výchozím podle věku profilu ([effective]). */
    val choice: StateFlow<Choice?> = _choice.asStateFlow()

    private fun readStored(): Choice? = when (prefs.getString(key(), null)) {
        "CZ" -> Choice.CZ
        "ORIGINAL" -> Choice.ORIGINAL
        else -> null
    }

    /** Přenačti po přepnutí profilu (klíč se mění s profilem). */
    fun refresh() {
        _choice.value = readStored()
    }

    /** Ulož volbu profilu; null = zpět na výchozí podle věku. */
    fun set(choice: Choice?) {
        prefs.edit().apply { if (choice == null) remove(key()) else putString(key(), choice.name) }.apply()
        _choice.value = choice
        Timber.i("[SEZONA] zvuk profilu %s = %s", profileKey(), choice?.name ?: "výchozí dle věku")
    }

    /** Volba profilu, jinak výchozí podle věku: dětský → CZ dabing, dospělý → originál. */
    fun effective(isChildProfile: Boolean): Choice =
        _choice.value ?: if (isChildProfile) Choice.CZ else Choice.ORIGINAL

    companion object {
        /**
         * Volba + původní jazyk titulu → seznam kódů pro `setPreferredAudioLanguages` (od nejžádanějšího).
         * Media3 chce ISO 639-2/B („eng"), soubory ale nesou i dvoupísmenné → posíláme oba tvary.
         * [originalLanguage] = TMDB `original_language` (dvoupísmenné, „en"/„ja"); prázdné → angličtina
         * jako nejčastější originál, ať se nikdy nespadne zpátky na „první stopa v pořadí".
         */
        fun languagesFor(choice: Choice, originalLanguage: String?): List<String> {
            val cz = listOf("ces", "cze", "cs", "slk", "slo", "sk")
            if (choice == Choice.CZ) return cz
            val two = originalLanguage?.trim()?.lowercase()?.takeIf { it.length == 2 } ?: "en"
            val three = ISO2_TO_ISO3[two]
            // Originál první, angličtina jako záchrana (u ne-anglických titulů bývá druhá nejrozšířenější),
            // čeština až úplně nakonec — pořád lepší než cizí stopa, kterou divák nezná.
            return (listOfNotNull(three, two) + listOf("eng", "en") + cz).distinct()
        }

        // Jen jazyky, které reálně potkáváme u filmů/seriálů — ne celá ISO tabulka.
        private val ISO2_TO_ISO3 = mapOf(
            "en" to "eng", "cs" to "ces", "sk" to "slk", "de" to "deu", "fr" to "fra",
            "es" to "spa", "it" to "ita", "ja" to "jpn", "ko" to "kor", "zh" to "zho",
            "ru" to "rus", "pl" to "pol", "pt" to "por", "sv" to "swe", "da" to "dan",
            "no" to "nor", "fi" to "fin", "nl" to "nld", "hu" to "hun", "tr" to "tur",
            "uk" to "ukr", "he" to "heb", "hi" to "hin", "ar" to "ara", "fa" to "fas",
        )

        /** Klíč v `traktPreferences`, kudy detail předá přehrávači žádané jazyky (čárkou oddělené). */
        const val PREF_PREFERRED_AUDIO_LANGS = "playback_preferred_audio_langs"
    }
}
