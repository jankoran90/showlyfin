package com.github.jankoran90.showlyfin.core.domain.lapidary

/**
 * LAPIDARY (SHW-96) — země sekce „Vzácné klenoty". ISO = klíč backendu (`canon.SUPPORTED_COUNTRIES`),
 * label = řada sekce. Start klíčová Asie; rozšiřitelné přidáním hodnoty + spuštěním `rebuild` na backendu.
 */
enum class LapidaryCountry(val iso: String, val label: String) {
    JP("JP", "Japonsko"),
    KR("KR", "Jižní Korea"),
    CN("CN", "Čína"),
    HK("HK", "Hongkong"),
    TW("TW", "Tchaj-wan"),
    TH("TH", "Thajsko"),
    IR("IR", "Írán"),
    ;

    companion object {
        /** Výchozí = všechny podporované země zapnuté (C4 Nastavení umožní vypnout). */
        val DEFAULT_ENABLED: Set<String> = entries.map { it.iso }.toSet()

        fun fromIso(iso: String): LapidaryCountry? = entries.firstOrNull { it.iso == iso }
    }
}
