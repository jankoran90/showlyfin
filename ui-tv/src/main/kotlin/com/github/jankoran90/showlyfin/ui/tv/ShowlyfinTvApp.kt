package com.github.jankoran90.showlyfin.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.LocalCsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.LocalCzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.core.ui.TvCardScale
import com.github.jankoran90.showlyfin.ui.phone.CardCsfdViewModel
import com.github.jankoran90.showlyfin.ui.phone.FontPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.theme.ShowlyfinPhoneTheme
import com.github.jankoran90.showlyfin.ui.tv.nav.TvNavigator

/**
 * TENFOOT (SHW-87) — vstupní bod nativního TV shellu. `MainActivity` ho volá místo `ShowlyfinApp`
 * pro leanback zařízení.
 *
 * Theme: Fáze 1 sdílí `ShowlyfinPhoneTheme` + `Theme/Font PrefsViewModel` z ui-phone (dočasná závislost),
 * takže AMOLED/akcent/písmo platí na TV identicky jako na telefonu. TODO Fáze 3: extrakce do core-theme.
 */
@Composable
fun ShowlyfinTvApp() {
    val themePrefs: ThemePrefsViewModel = hiltViewModel()
    val theme by themePrefs.state.collectAsStateWithLifecycle()
    val fontPrefs: FontPrefsViewModel = hiltViewModel()
    val font by fontPrefs.state.collectAsStateWithLifecycle()

    // ČSFD % + český popis na celé TV (parita s telefonem): stejný provider (CardCsfdViewModel) přes stejné
    // CompositionLocaly jako ShowlyfinPhoneApp → TV karty i immersive hero líně dotahují ČSFD/CZ z jednoho zdroje.
    val cardCsfd: CardCsfdViewModel = hiltViewModel()

    ShowlyfinPhoneTheme(
        themeState = theme,
        serifFont = font.serif,
        headingOnly = font.headingOnly,
        fontScale = font.scale,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            // Velikost UI (density) — jen TV shell. Škáluje celé rozhraní (karty, rozestupy, ikony) přes
            // LocalDensity. fontScale ponechán beze změny (text řeší už typografie přes FontPrefs.scale) →
            // žádné dvojí škálování textu. Telefon (ShowlyfinPhoneApp) tímto obalem neprochází → nedotčen.
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density * font.uiScale, base.fontScale),
                LocalCsfdRatingProvider provides cardCsfd,
                LocalCzechOverviewProvider provides cardCsfd,
                // COUCH DA4: globální šířka/rozestupy karet mřížky (všechny TV řady + Objevovat).
                LocalTvCardScale provides TvCardScale(widthScale = font.gridWidth, spacingScale = font.gridSpacing),
            ) {
                TvNavigator()
            }
        }
    }
}
