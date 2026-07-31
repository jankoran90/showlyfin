package com.github.jankoran90.showlyfin.feature.discover.curator

/**
 * Kategorie kurátorských doporučení (user 2026-07-31: „můžeme nějak oddělit kategoricky ty doporučení?
 * … umět jak kategorie nebo oddělení podle nedávno zhlédnutých a kladně hodnocených — pak sekce podle
 * mých top filmů").
 *
 * Dřív šel do mozku VŽDY celý vkus naráz a vypadl jeden nerozlišený balík „Pro tebe". Nově se pro každou
 * kategorii pošle jen ta VÝSEČ vkusu, která ji definuje — takže řada umí říct, PROČ tam ten film je.
 * `wire` jde na backend jako `bucket` a je součástí serverového cache klíče (jinak by kategorie
 * s prázdným `loved` kolidovaly a vrátily tentýž seznam).
 */
enum class CuratorBucket(val wire: String, val title: String) {
    /** Vkus = nejčastěji přehrávané (Trakt `plays`). */
    TOP("top", "Podle tvých nejsledovanějších"),
    /** Vkus = co má vysoké hvězdy (≥8) a oblíbené. */
    LOVED("loved", "Protože tohle hodnotíš vysoko"),
    /** Vkus = poslední zhlédnuté. */
    RECENT("recent", "Podle toho, cos viděl nedávno"),
}
