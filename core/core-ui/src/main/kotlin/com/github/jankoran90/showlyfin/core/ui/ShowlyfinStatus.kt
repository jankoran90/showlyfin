package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.ui.graphics.Color

/**
 * UNISON — sémantické (stavové) barvy nad rámec M3 `colorScheme` rolí. JEDINÝ zdroj pravdy;
 * feature kód čte odsud (`ShowlyfinStatus.X`) a NEdeklaruje vlastní `Color(0x…)`. Drží stavy
 * konzistentní napříč appkou (dřív duplikované — např. typy zdrojů v StreamComponents I SmartDetect,
 * ČSFD červená v CsfdComponents I PosterCard). Barvy ladí na AMOLED černou (viz claude-voice kanon).
 */
object ShowlyfinStatus {
    val Success = Color(0xFF4CAF50)          // úspěch / OK / zdravý zdroj / hotovo
    val SuccessDim = Color(0xFF2E7D32)       // tmavší zelená (badge „RD ✓ cached")
    val Warn = Color(0xFFFFC107)             // varování / čeká kontrolu / neúplné
    val Info = Color(0xFF2196F3)             // info / shlédnuto / přesunuto
    val Danger = Color(0xFFE53935)           // destruktivní akce / vypnuto

    // Typy Stremio/RD zdrojů v pickeru
    val SourceRdSaved = Color(0xFF6A1B9A)    // už uložené na RD (hraje hned)
    val SourceRdDownload = Color(0xFFE08915) // necachované, RD stáhne
    val SourceTorrent = Color(0xFF1565C0)    // torrent
    val SourceAddon = Color(0xFFB23A3A)      // jiný addon / fallback

    // ČSFD hodnocení
    val CsfdHigh = Color(0xFFBA0305)         // silné hodnocení (červená ČSFD)
    val CsfdLow = Color(0xFF5E86B0)          // slabší film (pastelová modrá)
}
