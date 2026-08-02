package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REPACK — paměť „tenhle zdroj se bez přebalu nepřehraje".
 *
 * 🔴 Proč vznikla (user 2026-08-02): *„je tam pořád hledám zdroje a po 20 s přebaluju a pak až přehraje.
 * To mi vadí, že to není plynulé. A když se vracím zpět, tak celý proces běží znovu. Kdybych měl
 * autopřehrávání další epizody, tak to je nepraktické."*
 *
 * Přebal se dosud spouštěl AŽ ve chvíli, kdy přehrávač na formátové chybě spadl — to je u každého
 * spuštění stejná ztráta času (načítání → pád → teprve pak přebal). Jakmile ale jednou víme, že zdroj
 * potřebuje přebal, není na co čekat: podruhé jdeme rovnou na přebalený soubor.
 *
 * Klíč je identita OBSAHU (viz volající), ne playback adresa — ty jsou u AIOStreams/RD podepsané
 * a krátkodobé, takže by paměť po re-resolve nikdy netrefila. Sada je omezená ([MAX_ENTRIES]);
 * při přetečení se zahazují nejstarší záznamy — cena za výpadek je jen jeden pomalý start.
 */
@Singleton
class RepackNeededStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("repack_needed", Context.MODE_PRIVATE)

    fun needsRepack(identity: String): Boolean =
        identity.isNotBlank() && order().contains(identity)

    fun remember(identity: String) {
        if (identity.isBlank()) return
        val current = order()
        if (current.firstOrNull() == identity) return
        val next = (listOf(identity) + current.filter { it != identity }).take(MAX_ENTRIES)
        prefs.edit().putString(KEY_ORDER, next.joinToString(SEPARATOR)).apply()
        Timber.i("[REPACK] zdroj označen jako „vyžaduje přebal" (%d v paměti)", next.size)
    }

    /** Přebal přestal být potřeba (zdroj hraje i bez něj) → zapomeň, ať se zbytečně nepřebaluje. */
    fun forget(identity: String) {
        if (identity.isBlank()) return
        val current = order()
        if (identity !in current) return
        prefs.edit().putString(KEY_ORDER, current.filter { it != identity }.joinToString(SEPARATOR)).apply()
    }

    /** Nejnovější první. Jeden řetězec místo `StringSet`, protože pořadí je tu součástí informace. */
    private fun order(): List<String> =
        prefs.getString(KEY_ORDER, "").orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }

    private companion object {
        const val KEY_ORDER = "identities"
        const val SEPARATOR = "\n"
        const val MAX_ENTRIES = 300
    }
}
