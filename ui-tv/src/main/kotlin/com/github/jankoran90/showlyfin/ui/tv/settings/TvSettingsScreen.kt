package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.SidebarEntry
import com.github.jankoran90.showlyfin.core.domain.home.SidebarItem
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.ui.phone.FontPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel
import com.github.jankoran90.showlyfin.ui.phone.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.theme.Background
import com.github.jankoran90.showlyfin.ui.phone.theme.ShowlyfinSkin
import kotlin.math.roundToInt

/**
 * TENFOOT (SHW-87) F3 — nativní 10-foot Nastavení na TV. Sdílí tytéž ViewModely co telefon
 * ([ThemePrefsViewModel], [FontPrefsViewModel], [SettingsViewModel]) → změny se projeví napříč
 * (prefs jsou activity-scoped, náhled naživo). Vše přes D-pad ± steppery / přepínače, žádný `Slider`.
 *
 * Zaměřeno na to, co člověk ladí u televize: vzhled (motiv, barevné osy, písmo), obraz/zvuk (DRC filmu),
 * přehrávač (transport lišta), účty (Trakt přes device-code — bez prohlížeče), systém. Pokročilé profilové
 * věci zůstávají v telefonním Nastavení.
 */
@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    themePrefs: ThemePrefsViewModel = hiltViewModel(),
    fontPrefs: FontPrefsViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
    homeVm: TvHomeViewModel = hiltViewModel(),
) {
    val theme by themePrefs.state.collectAsStateWithLifecycle()
    val font by fontPrefs.state.collectAsStateWithLifecycle()
    val sys by settings.uiState.collectAsStateWithLifecycle()
    val sidebar by homeVm.sidebar.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize().tvOverscan(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zpět",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .tvFocusBorder(shape = CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(8.dp),
                )
                Text("Nastavení", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        // ── Vzhled ──
        item {
            TvSettingsBlock(title = "Vzhled") {
                TvOptionStepperRow(
                    label = "Pozadí",
                    options = Background.entries.toList(),
                    selected = theme.background,
                    labelOf = { it.displayName },
                    onSelect = themePrefs::setBackground,
                )
                TvOptionStepperRow(
                    label = "Motiv",
                    subtitle = "Barevné schéma akcentu",
                    options = ShowlyfinSkin.entries.toList(),
                    selected = theme.skin,
                    labelOf = { it.displayName },
                    onSelect = themePrefs::setSkin,
                )
                TvValueStepperRow(
                    label = "Tónování ploch",
                    percent = (theme.surfaceTint * 100).roundToInt(),
                    onPercent = { themePrefs.setSurfaceTint(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Světlost ploch",
                    percent = (theme.surfaceLightness * 100).roundToInt(),
                    onPercent = { themePrefs.setSurfaceLightness(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Síla akcentu",
                    percent = (theme.accentStrength * 100).roundToInt(),
                    onPercent = { themePrefs.setAccentStrength(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Tónování prvků",
                    percent = (theme.containerTint * 100).roundToInt(),
                    onPercent = { themePrefs.setContainerTint(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Kontrast textu",
                    percent = (theme.textContrast * 100).roundToInt(),
                    onPercent = { themePrefs.setTextContrast(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Sytost barev",
                    percent = (theme.accentChroma * 100).roundToInt(),
                    onPercent = { themePrefs.setAccentChroma(it / 100f) },
                )
            }
        }

        // ── Písmo ──
        item {
            TvSettingsBlock(title = "Písmo") {
                TvToggleRow(
                    label = "Patkové písmo",
                    subtitle = "Newsreader místo systémového",
                    checked = font.serif,
                    onCheckedChange = fontPrefs::setSerif,
                )
                if (font.serif) {
                    TvToggleRow(
                        label = "Jen na nadpisy",
                        checked = font.headingOnly,
                        onCheckedChange = fontPrefs::setHeadingOnly,
                    )
                }
                TvOptionStepperRow(
                    label = "Velikost písma",
                    options = FontPrefsViewModel.SCALE_OPTIONS,
                    selected = font.scalePct,
                    labelOf = { "$it %" },
                    onSelect = fontPrefs::setScalePct,
                )
            }
        }

        // ── Obraz a zvuk ──
        item {
            TvSettingsBlock(title = "Obraz a zvuk") {
                TvOptionStepperRow(
                    label = "Normalizace hlasitosti filmu",
                    subtitle = "Ztlumí hlasité scény, zesílí ticho",
                    options = listOf(0, 1, 2, 3),
                    selected = sys.movieDrcLevel,
                    labelOf = ::drcLabel,
                    onSelect = settings::setMovieDrcLevel,
                )
                TvToggleRow(
                    label = "Passthrough zvuku (5.1 do AVR)",
                    subtitle = "Přijímač dekóduje zvuk sám — opraví rozjetý zvuk/obraz. Vypni, jen když je ticho",
                    checked = sys.playerTvAudioPassthrough,
                    onCheckedChange = settings::setPlayerTvAudioPassthrough,
                )
            }
        }

        // ── Přehrávač ──
        item {
            TvSettingsBlock(title = "Přehrávač") {
                TvOptionStepperRow(
                    label = "Skrýt ovládání po",
                    subtitle = "Nečinnost, po které zmizí lišta přehrávače",
                    options = PlayerPrefs.CONTROLS_HIDE_SEC_OPTIONS,
                    selected = sys.playerControlsHideSec,
                    labelOf = ::hideDelayLabel,
                    onSelect = settings::setPlayerControlsHideSec,
                )
                TvOptionStepperRow(
                    label = "Krok převíjení",
                    subtitle = "O kolik posunou tlačítka ⏮ ⏭ a osa",
                    options = PlayerPrefs.SEEK_STEP_SEC_OPTIONS,
                    selected = sys.playerSeekStepSec,
                    labelOf = { "$it s" },
                    onSelect = settings::setPlayerSeekStepSec,
                )
            }
        }

        // ── Účty ──
        item {
            TvSettingsBlock(title = "Účty") {
                TvTraktAccountRow(
                    loggedIn = sys.traktLoggedIn,
                    userCode = sys.traktUserCode,
                    verificationUrl = sys.traktVerificationUrl,
                    status = sys.traktStatus,
                    onLogin = settings::startTraktDeviceLogin,
                    onLogout = settings::logout,
                )
            }
        }

        // ── Domů / Postranní menu ──
        item {
            TvSettingsBlock(title = "Domů a postranní menu") {
                Text(
                    text = "Které sekce ukazovat v levém menu (řady domova se ladí přímo na domově tlačítkem Menu):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                sidebar.forEachIndexed { index, entry ->
                    SidebarEditorRow(
                        entry = entry,
                        index = index,
                        count = sidebar.size,
                        onMove = { up -> homeVm.moveSidebar(entry.item, up) },
                        onToggle = { on -> homeVm.setSidebarEnabled(entry.item, on) },
                    )
                }
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TvActionChip(
                        label = "Obnovit výchozí domov",
                        enabled = true,
                        onClick = { homeVm.resetRows() },
                    )
                }
            }
        }

        // ── Systém ──
        item {
            TvSettingsBlock(title = "Systém") {
                TvToggleRow(
                    label = "Živé logování",
                    subtitle = "Odesílá diagnostické logy na server",
                    checked = sys.liveLogging,
                    onCheckedChange = settings::setLiveLogging,
                )
            }
        }
    }
}

/** Řádek editoru sidebaru: název sekce + přesun ↑/↓ + zap/vyp (vše D-pad chipy). */
@Composable
private fun SidebarEditorRow(
    entry: SidebarEntry,
    index: Int,
    count: Int,
    onMove: (up: Boolean) -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val item = SidebarItem.fromName(entry.item) ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TvActionChip(label = "↑", enabled = index > 0, onClick = { onMove(true) })
        TvActionChip(label = "↓", enabled = index < count - 1, onClick = { onMove(false) })
        TvActionChip(
            label = if (entry.enabled) "Zap" else "Vyp",
            enabled = true,
            danger = !entry.enabled,
            onClick = { onToggle(!entry.enabled) },
        )
    }
}

private fun drcLabel(level: Int): String = when (level) {
    0 -> "Vypnuto"
    1 -> "Mírná"
    2 -> "Střední"
    else -> "Noční"
}

private fun hideDelayLabel(sec: Int): String = if (sec <= 0) "Nikdy" else "$sec s"
