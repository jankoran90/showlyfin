package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** AGORA-TABS: záložky sekce Podcasty — pořadí pevné, Timeline je default. */
enum class PodcastTab(val pref: String, val label: String) {
    TIMELINE("timeline", "Timeline"),
    FOLLOWING("following", "Sledované"),
    DISCOVER("discover", "Objev");

    companion object {
        fun fromPref(value: String?): PodcastTab = entries.firstOrNull { it.pref == value } ?: TIMELINE
    }
}

/** Saver pro rememberSaveable (přežije rotaci) — ukládá `pref` string. */
val PodcastTabSaver: Saver<PodcastTab, String> = Saver(
    save = { it.pref },
    restore = { PodcastTab.fromPref(it) },
)

/**
 * AGORA-TABS: přepínací řada sekce Podcasty. Úplně PRVNÍ prvek = ikona filtru (otevře filtr „co chci
 * vidět"), za ní segmentové přepínače Timeline · Sledované · Objev (styl jako přepínač Audioknihy ↔
 * Podcasty výš v Poslechu). Vše čte z [androidx.compose.material3.MaterialTheme] tokenů (UNISON).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastTabRow(
    selected: PodcastTab,
    onSelect: (PodcastTab) -> Unit,
    onOpenFilter: () -> Unit,
    activeFilterCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1) Filtr — úplně první prvek řady.
        BadgedBox(
            badge = { if (activeFilterCount > 0) Badge { Text("$activeFilterCount") } },
        ) {
            if (activeFilterCount > 0) {
                FilledIconButton(onClick = onOpenFilter, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = "Filtr podcastů")
                }
            } else {
                FilledTonalIconButton(onClick = onOpenFilter, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = "Filtr podcastů")
                }
            }
        }

        // 2) Timeline · Sledované · Objev
        val tabs = PodcastTab.entries
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            tabs.forEachIndexed { i, tab ->
                SegmentedButton(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = tabs.size),
                ) { Text(tab.label, maxLines = 1) }
            }
        }
    }
}
