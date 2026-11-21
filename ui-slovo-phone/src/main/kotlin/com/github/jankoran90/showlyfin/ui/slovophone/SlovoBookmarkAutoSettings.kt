package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.player.ListenPlayerPrefs
import com.github.jankoran90.showlyfin.feature.listen.service.AutoTabsPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** AMPHORA F5 — Nastavení › Záložky a Nastavení › Android Auto. */
@HiltViewModel
class SlovoBookmarkAutoPrefsViewModel @Inject constructor(
    val listen: ListenPlayerPrefs,
    val auto: AutoTabsPrefs,
) : ViewModel()

private val HEADSET = listOf(ListenPlayerPrefs.HEADSET_BOOKMARK, ListenPlayerPrefs.HEADSET_SYSTEM)

/** Záložky (PR2): dvojklik sluchátek, poznámka hned, pípnutí, značky na ose. */
@Composable
internal fun SlovoBookmarkSettingsSection(vm: SlovoBookmarkAutoPrefsViewModel = hiltViewModel()) {
    val lp = vm.listen
    var headset by remember { mutableStateOf(lp.headsetDoubleTap) }
    var askNote by remember { mutableStateOf(lp.bookmarkAskNote) }
    var beep by remember { mutableStateOf(lp.bookmarkBeep) }
    var marks by remember { mutableStateOf(lp.bookmarkMarks) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SettingChips(
            label = "Dvojklik na sluchátkách",
            subtitle = "Záložka = uloží místo, jeden stisk dál přehrává a pozastavuje. Jinak přeskočí na další, jak to dělá systém",
            options = HEADSET, selected = headset,
            labelOf = { if (it == ListenPlayerPrefs.HEADSET_BOOKMARK) "Záložka" else "Přeskočit dál" },
            onSelect = { lp.headsetDoubleTap = it; headset = it },
        )
        SettingSwitchRow(
            title = "Pípnout po záložce ze sluchátek a z auta",
            subtitle = "Krátké potvrzení, když nevidíš na displej",
            checked = beep, onCheckedChange = { lp.bookmarkBeep = it; beep = it },
        )
        SettingSwitchRow(
            title = "Hned se zeptat na poznámku",
            subtitle = "Po ťuknutí na Záložka v přehrávači. Vypnuto = jen potvrzení s tlačítkem Poznámka",
            checked = askNote, onCheckedChange = { lp.bookmarkAskNote = it; askNote = it },
        )
        SettingSwitchRow(
            title = "Značky záložek na časové ose",
            checked = marks, onCheckedChange = { lp.bookmarkMarks = it; marks = it },
        )
        Text(
            "Všechny záložky najdeš v nabídce Víc › Záložky, záložky knihy i v jejím detailu. Ukládají se na server, takže je uvidíš i v jiném telefonu.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Android Auto (IN2): které karty a v jakém pořadí, knihovny zvlášť. */
@Composable
internal fun SlovoAutoSettingsSection(vm: SlovoBookmarkAutoPrefsViewModel = hiltViewModel()) {
    val ap = vm.auto
    // Všechny karty: zapnuté v uloženém pořadí, vypnuté za nimi.
    var order by remember { mutableStateOf(ap.tabs + AutoTabsPrefs.Tab.entries.filter { it !in ap.tabs }) }
    var enabled by remember { mutableStateOf(ap.tabs.toSet()) }
    var libTabs by remember { mutableStateOf(ap.librariesAsTabs) }
    fun save() { ap.tabs = order.filter { it in enabled } }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "Auto ukáže nahoře nejvýš ${AutoTabsPrefs.MAX_TABS} karty, další se schovají. Záložky bez vlastní karty jsou na začátku Pokračovat. Změna se projeví po novém připojení k autu.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        order.forEachIndexed { i, tab ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = tab in enabled,
                    onCheckedChange = { on -> enabled = if (on) enabled + tab else enabled - tab; save() },
                )
                Text(tab.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(enabled = i > 0, onClick = {
                    order = order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }; save()
                }) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Výš") }
                IconButton(enabled = i < order.lastIndex, onClick = {
                    order = order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }; save()
                }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Níž") }
            }
        }
        SettingSwitchRow(
            title = "Knihovny jako samostatné karty",
            subtitle = "Dětské a Dospělý každá zvlášť místo jedné karty Knihy (zabere víc míst)",
            checked = libTabs, onCheckedChange = { ap.librariesAsTabs = it; libTabs = it },
        )
    }
}
