package com.github.jankoran90.showlyfin.core.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * SUBSTRATE (SHW-99) F3 / EXCISE (SHW-103) Fáze B — doména OBLÍBENÉ POŘADY (srdíčka) v [substrate.db].
 *
 * Osobní záložky podcastů/kanálů ([com.github.jankoran90.showlyfin.feature.listen.player.FavoriteSourcesStore])
 * — dosud jen lokální (per-device). Tahle tabulka je synchronizuje cross-device per profil (`slovo-main`)
 * přes generický delta sync. Odlišné od SDÍLENÉHO serverového store zdrojů (PodcastSourcesRepository = „Přidáno").
 *
 * Sync mixin jako [FavoriteEntity]. Identita řádku = `type:ref` ([type] bez dvojtečky, [ref] smí ji obsahovat
 * = RSS feed URL). Ukládáme CELOU kartu, ať se „Oblíbené" vykreslí i offline (bez serverového dotazu).
 */
@Entity(
    tableName = "saved_show",
    primaryKeys = ["profileKey", "type", "ref"],
    indices = [Index("profileKey")],
)
data class SavedShowEntity(
    val profileKey: String,
    /** "youtube" | "rss" | "ctv". */
    val type: String,
    val ref: String,
    val title: String = "",
    val subtitle: String? = null,
    val thumbnail: String? = null,
    val summary: String? = null,
    val episodeCount: Int? = null,
    val category: String? = null,
    /** Čas přidání srdíčka (ms) — řazení „nejnovější první". */
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncVersion: Long = 0L,
    val dirty: Int = 0,
    val deleted: Int = 0,
)
