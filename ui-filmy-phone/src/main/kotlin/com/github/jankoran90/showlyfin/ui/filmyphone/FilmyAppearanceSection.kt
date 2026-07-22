package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.theme.FontPrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.Background
import com.github.jankoran90.showlyfin.core.theme.ShowlyfinSkin

/**
 * CELLULOID (SHW-98) M2.7 Settings parita — blok „Vzhled" + „Písmo" v Nastavení Filmy.
 * Reuse SDÍLENÝCH [ThemePrefsViewModel]/[FontPrefsViewModel] (activity-scoped, tytéž instance co
 * [FilmyPhoneShell] → změna se propíše do motivu ŽIVĚ). Touch ovladače ([FilmySettingRows]) místo
 * TV D-pad stepperů; logika 1:1 s TV `TvSettingsScreen` blokem Vzhled/Písmo. Immersive/velikost-UI
 * jsou TV-specifické (telefon shell jimi neprochází) → vynechány.
 */
@Composable
fun FilmyAppearanceSection(
    themePrefs: ThemePrefsViewModel = hiltViewModel(),
    fontPrefs: FontPrefsViewModel = hiltViewModel(),
) {
    val theme by themePrefs.state.collectAsStateWithLifecycle()
    val font by fontPrefs.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Vzhled")

        SettingChips(
            label = "Motiv",
            options = ShowlyfinSkin.entries,
            selected = theme.skin,
            labelOf = { it.displayName },
            onSelect = themePrefs::setSkin,
        )
        SettingChips(
            label = "Pozadí",
            options = Background.entries,
            selected = theme.background,
            labelOf = { it.displayName },
            onSelect = themePrefs::setBackground,
        )

        // ORCHARD (user 07-19) — počet sloupců mřížky (Auto/2/3). Ukládá se do trakt_prefs „grid_columns"
        // (0=auto dle šířky). Projeví se při návratu na obrazovku s mřížkou. Platí napříč Filmotéka/Knihovna/Hledat.
        val ctx = LocalContext.current
        var gridCols by remember {
            mutableIntStateOf(ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE).getInt("grid_columns", 0))
        }
        SettingChips(
            label = "Počet sloupců mřížky",
            subtitle = "Kolik plakátů vedle sebe v zobrazení mřížky (Auto = podle šířky). Projeví se po návratu na seznam.",
            options = listOf(0, 2, 3),
            selected = gridCols,
            labelOf = { if (it == 0) "Auto" else it.toString() },
            onSelect = { v ->
                gridCols = v
                ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE).edit().putInt("grid_columns", v).apply()
            },
        )

        SettingPercentSlider("Tónování ploch", value = theme.surfaceTint, onValue = themePrefs::setSurfaceTint)
        SettingPercentSlider("Světlost ploch", value = theme.surfaceLightness, onValue = themePrefs::setSurfaceLightness)
        SettingPercentSlider("Síla akcentu", value = theme.accentStrength, onValue = themePrefs::setAccentStrength)
        SettingPercentSlider("Tónování prvků", value = theme.containerTint, onValue = themePrefs::setContainerTint)
        SettingPercentSlider("Kontrast textu", value = theme.textContrast, onValue = themePrefs::setTextContrast)
        SettingPercentSlider("Sytost barev", value = theme.accentChroma, onValue = themePrefs::setAccentChroma)

        SettingSectionTitle("Písmo")
        SettingSwitchRow(
            title = "Patkové písmo",
            subtitle = "Newsreader místo systémového",
            checked = font.serif,
            onCheckedChange = fontPrefs::setSerif,
        )
        if (font.serif) {
            SettingSwitchRow(
                title = "Jen na nadpisy",
                checked = font.headingOnly,
                onCheckedChange = fontPrefs::setHeadingOnly,
            )
        }
        SettingChips(
            label = "Velikost písma",
            options = FontPrefsViewModel.SCALE_OPTIONS,
            selected = font.scalePct,
            labelOf = { "$it %" },
            onSelect = fontPrefs::setScalePct,
        )
    }
}
