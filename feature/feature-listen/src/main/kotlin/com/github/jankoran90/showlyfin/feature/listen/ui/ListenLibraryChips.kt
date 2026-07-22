package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Vodorovné chips pro výběr knihovny (Audioknihy i Sledované podcasty). Vyříznuto z [ListenScreen]
 * (anti-monolit) — sdíleno mezi BooksContent a FollowingContent, proto `internal`.
 */
@Composable
internal fun LibraryChips(
    libraries: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        libraries.forEach { (id, name) ->
            FilterChip(
                selected = id == selectedId,
                onClick = { onSelect(id) },
                label = { Text(name) },
            )
        }
    }
}

/**
 * WEFT (SHW-75/W3): položka knihovny Sledovaných pro JEDEN abecedně řazený grid (merged + zdroj + ABS).
 * Vyříznuto z [ListenScreen]; používá ho FollowingContent, proto `internal`.
 */
internal sealed interface LibraryCard {
    val sortTitle: String
    val itemKey: String
    /** WEFT (SHW-75/W5): klíče pro skrytí (sloučená = všichni členové, zdroj = `type:ref`, ABS = `abs:id`). */
    val hideKeys: Set<String>

    data class Merged(val groupId: String, val title: String, val thumbnail: String?, val members: List<String>) : LibraryCard {
        override val sortTitle get() = title
        override val itemKey get() = "lg:$groupId"
        override val hideKeys get() = members.toSet()
    }

    data class Plain(val source: com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource, val key: String) : LibraryCard {
        override val sortTitle get() = source.title
        override val itemKey get() = "src:${source.id}"
        override val hideKeys get() = setOf(key)
    }

    data class Abs(val podcast: com.github.jankoran90.showlyfin.data.abs.model.Podcast) : LibraryCard {
        override val sortTitle get() = podcast.title
        override val itemKey get() = "abs:${podcast.id}"
        override val hideKeys get() = setOf("abs:${podcast.id}")
    }
}
