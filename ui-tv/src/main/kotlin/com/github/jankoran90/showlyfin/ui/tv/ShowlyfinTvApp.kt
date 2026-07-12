package com.github.jankoran90.showlyfin.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            TvNavigator()
        }
    }
}
