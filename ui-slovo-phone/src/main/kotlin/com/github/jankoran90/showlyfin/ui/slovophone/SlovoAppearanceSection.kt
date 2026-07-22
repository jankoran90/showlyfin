package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.theme.Background
import com.github.jankoran90.showlyfin.core.theme.FontPrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.ShowlyfinSkin
import com.github.jankoran90.showlyfin.core.theme.ThemePrefsViewModel

/**
 * Slovo (EXCISE/SHW-103) — sekce „Vzhled": AMOLED motiv sdílený s Filmy (stejné activity-scoped VM
 * [ThemePrefsViewModel]/[FontPrefsViewModel] z :core:core-theme → změna se projeví ŽIVĚ). Barva (skin),
 * pozadí, jemné ladění ploch/akcentu posuvníky, písmo. Kánon: víc voleb = spokojenější (progressive disclosure).
 */
@Composable
internal fun SlovoAppearanceSection(
    themePrefs: ThemePrefsViewModel = hiltViewModel(),
    fontPrefs: FontPrefsViewModel = hiltViewModel(),
) {
    val theme by themePrefs.state.collectAsStateWithLifecycle()
    val font by fontPrefs.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingChips(
            label = "Barva (akcent)",
            options = ShowlyfinSkin.entries.toList(),
            selected = theme.skin,
            labelOf = { it.displayName },
            onSelect = { themePrefs.setSkin(it) },
        )
        SettingChips(
            label = "Pozadí",
            options = Background.entries.toList(),
            selected = theme.background,
            labelOf = { it.displayName },
            onSelect = { themePrefs.setBackground(it) },
        )
        SettingPercentSlider("Tónování ploch", value = theme.surfaceTint) { themePrefs.setSurfaceTint(it) }
        SettingPercentSlider("Světlost ploch", value = theme.surfaceLightness) { themePrefs.setSurfaceLightness(it) }
        SettingPercentSlider("Síla akcentu", value = theme.accentStrength) { themePrefs.setAccentStrength(it) }
        SettingPercentSlider("Tónování kontejnerů", value = theme.containerTint) { themePrefs.setContainerTint(it) }
        SettingPercentSlider("Kontrast textu", value = theme.textContrast) { themePrefs.setTextContrast(it) }
        SettingPercentSlider("Sytost akcentu", value = theme.accentChroma) { themePrefs.setAccentChroma(it) }

        SettingSwitchRow(
            title = "Patkové písmo",
            subtitle = "Newsreader místo systémového bezpatkového",
            checked = font.serif,
            onCheckedChange = { fontPrefs.setSerif(it) },
        )
        if (font.serif) {
            SettingSwitchRow(
                title = "Patky jen na nadpisy",
                checked = font.headingOnly,
                onCheckedChange = { fontPrefs.setHeadingOnly(it) },
            )
        }
        SettingChips(
            label = "Velikost textu",
            options = FontPrefsViewModel.SCALE_OPTIONS,
            selected = font.scalePct,
            labelOf = { "$it %" },
            onSelect = { fontPrefs.setScalePct(it) },
        )
    }
}
