package com.github.jankoran90.showlyfin.ui.slovophone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.feature.listen.QueueGestureSettingsViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.ListenPlayerPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** AMPHORA F4 — přístup Nastavení › Přehrávač k volbám přehrávače (sdílené AbsPreferences + Slovo ListenPlayerPrefs). */
@HiltViewModel
class SlovoPlayerPrefsViewModel @Inject constructor(
    val abs: AbsPreferences,
    val listen: ListenPlayerPrefs,
    @param:ApplicationContext private val ctx: Context,
) : ViewModel() {
    /** Služba přehrávače si DRC / přeskočení ticha přečte hned. */
    fun notifyService() = ListenPlayerPrefs.notifyService(ctx)
}

private val SPEEDS = listOf(0.8f, 1f, 1.1f, 1.25f, 1.5f, 2f)
private val SKIPS = listOf(10, 15, 30, 60)
private val REWINDS = listOf(10, 20, 30, 60)
private val DRC_LABELS = listOf("Vyp", "Mírné", "Střední", "Silné", "Noční")

/**
 * Nastavení → Přehrávač (AMPHORA F4 / PR1+PR3): rychlost, skoky, přetočení po pauze, spánek, hlas,
 * ticho, vzhled přehrávače a gesta fronty. Volby se projeví při příštím spuštění, DRC a ticho hned.
 */
@Composable
internal fun SlovoPlayerSettingsSection(
    viewModel: QueueGestureSettingsViewModel = hiltViewModel(),
    prefsVm: SlovoPlayerPrefsViewModel = hiltViewModel(),
) {
    val left by viewModel.left.collectAsStateWithLifecycle()
    val right by viewModel.right.collectAsStateWithLifecycle()
    val abs = prefsVm.abs
    val lp = prefsVm.listen
    var speed by remember { mutableStateOf(abs.defaultSpeed) }
    var rememberSpeed by remember { mutableStateOf(lp.rememberLastSpeed) }
    var skip by remember { mutableStateOf(abs.skipSeconds) }
    var rewind by remember { mutableStateOf(lp.rewindAfterPause) }
    var rewindMax by remember { mutableStateOf(lp.rewindMaxSec) }
    var fade by remember { mutableStateOf(lp.sleepFade) }
    var shake by remember { mutableStateOf(lp.sleepShake) }
    var wake by remember { mutableStateOf(lp.sleepWakeOffer) }
    var drc by remember { mutableStateOf(abs.listenDrcLevel) }
    var silence by remember { mutableStateOf(lp.skipSilence) }
    var tint by remember { mutableStateOf(lp.colorFromCover) }
    var floating by remember { mutableStateOf(lp.floatingMini) }
    var remaining by remember { mutableStateOf(abs.showRemainingTime) }
    var speedBtn by remember { mutableStateOf(abs.showSpeedButton) }
    var sleepBtn by remember { mutableStateOf(abs.showSleepButton) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Group("Rychlost a skoky")
        SettingChips(
            label = "Výchozí rychlost",
            subtitle = "Každý díl i kniha se pustí touto rychlostí",
            options = SPEEDS, selected = speed, labelOf = { "${trim(it)}×" },
            onSelect = { abs.defaultSpeed = it; speed = it },
        )
        SettingSwitchRow(
            title = "Pamatovat poslední rychlost",
            subtitle = "Zvlášť knihy a pořady. Vypnuto = vždy výchozí rychlost",
            checked = rememberSpeed,
            onCheckedChange = { lp.rememberLastSpeed = it; rememberSpeed = it },
        )
        SettingChips(
            label = "Skok zpět / vpřed",
            options = SKIPS, selected = skip, labelOf = { "$it s" },
            onSelect = { abs.skipSeconds = it; skip = it },
        )
        SettingSwitchRow(
            title = "Po pauze přetočit kus zpět",
            subtitle = "Čím delší pauza, tím víc (krátká pauza nic). Platí i ze sluchátek a v autě",
            checked = rewind,
            onCheckedChange = { lp.rewindAfterPause = it; rewind = it },
        )
        if (rewind) SettingChips(
            label = "Nejvíc po dlouhé pauze",
            options = REWINDS, selected = rewindMax, labelOf = { "$it s" },
            onSelect = { lp.rewindMaxSec = it; rewindMax = it },
        )

        Group("Spánek")
        SettingSwitchRow(
            title = "Před koncem ztišit",
            subtitle = "Posledních 30 s časovače postupně ztlumí",
            checked = fade, onCheckedChange = { lp.sleepFade = it; fade = it },
        )
        SettingSwitchRow(
            title = "Zatřesením prodloužit",
            subtitle = "Zatřes telefonem a časovač poběží znovu celý (krátce cvrnkne)",
            checked = shake, onCheckedChange = { lp.sleepShake = it; shake = it },
        )
        SettingSwitchRow(
            title = "Nabídnout návrat, kde jsi usnul",
            subtitle = "Ráno v přehrávači: „Usnul jsi asi v…, vrátit?“",
            checked = wake, onCheckedChange = { lp.sleepWakeOffer = it; wake = it },
        )

        Group("Zvuk")
        SettingChips(
            label = "Vyrovnání hlasitosti",
            subtitle = "Zvedne tiché nahrávky. „Zesílit hlas“ v přehrávači přepne jen daný pořad na Silné",
            options = DRC_LABELS.indices.toList(), selected = drc, labelOf = { DRC_LABELS[it] },
            onSelect = { abs.listenDrcLevel = it; drc = it; prefsVm.notifyService() },
        )
        SettingSwitchRow(
            title = "Přeskakovat ticho",
            subtitle = "Zkrátí pauzy v řeči. Délky a časy dílů pak nesedí přesně",
            checked = silence,
            onCheckedChange = { lp.skipSilence = it; silence = it; prefsVm.notifyService() },
        )

        Group("Vzhled přehrávače")
        SettingSwitchRow(
            title = "Barva podle obalu",
            subtitle = "Pozadí přehrávače se zbarví podle obalu. Vypnuto = barvy motivu",
            checked = tint, onCheckedChange = { lp.colorFromCover = it; tint = it },
        )
        SettingSwitchRow(
            title = "Plovoucí mini přehrávač",
            subtitle = "Karta nad obsahem. Vypnuto = lišta přes celou šířku",
            checked = floating, onCheckedChange = { lp.floatingMini = it; floating = it },
        )
        SettingSwitchRow(
            title = "Ukazovat zbývající čas",
            checked = remaining, onCheckedChange = { abs.showRemainingTime = it; remaining = it },
        )
        SettingSwitchRow(
            title = "Tlačítko rychlosti",
            checked = speedBtn, onCheckedChange = { abs.showSpeedButton = it; speedBtn = it },
        )
        SettingSwitchRow(
            title = "Tlačítko časovače spánku",
            checked = sleepBtn, onCheckedChange = { abs.showSleepButton = it; sleepBtn = it },
        )

        Group("Fronta")
        SettingChips(
            label = "Fronta: swipe doleva",
            subtitle = "Vypnuto = položka fronty se tažením nehne (žádné omylem odebrané díly).",
            options = QueueGestureSettingsViewModel.OPTIONS,
            selected = left,
            labelOf = QueueGestureSettingsViewModel::label,
            onSelect = viewModel::setLeft,
        )
        SettingChips(
            label = "Fronta: swipe doprava",
            options = QueueGestureSettingsViewModel.OPTIONS,
            selected = right,
            labelOf = QueueGestureSettingsViewModel::label,
            onSelect = viewModel::setRight,
        )
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var showContinue by remember { mutableStateOf(com.github.jankoran90.showlyfin.feature.listen.ui.QueuePrefs.showContinue(ctx)) }
        SettingSwitchRow(
            title = "Rozposlouchané pod frontou",
            subtitle = "Obrazovka Fronta ukáže i rozjeté knihy a díly (tah doprava = přehrát další, doleva = ukončit poslech)",
            checked = showContinue,
            onCheckedChange = { com.github.jankoran90.showlyfin.feature.listen.ui.QueuePrefs.setShowContinue(ctx, it); showContinue = it },
        )
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

private fun trim(s: Float): String = if (s == s.toLong().toFloat()) s.toLong().toString() else s.toString().replace('.', ',')
