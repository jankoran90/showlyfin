package com.github.jankoran90.showlyfin.ui.tv.components

import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowItem

/**
 * COUCH — sdílený mapping Jellyfin řadové položky ([LibraryRowItem]) na sjednocený [HomeRowItem].
 * Používá Domů (JF knihovní řady) i sekce Knihovna (Fáze B: řádkový model přes `TvRailList`).
 */
internal fun LibraryRowItem.toHomeRowItem() = HomeRowItem(
    key = "jf_$jellyfinId",
    title = name,
    year = year,
    posterUrl = imageUrl,
    landscapeUrl = landscapeUrl,
    progressPct = progressPct,
    watched = watched,
    mediaItem = mediaItem,
    jellyfinId = jellyfinId,
)
