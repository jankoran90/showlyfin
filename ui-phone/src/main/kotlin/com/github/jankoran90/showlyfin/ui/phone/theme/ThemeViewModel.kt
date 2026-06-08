package com.github.jankoran90.showlyfin.ui.phone.theme

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.data.theme.SkinController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Most mezi singletonem [SkinController] a Compose vrstvou (Plan PRISM Fáze 2).
 * `state` konzumuje `ShowlyfinPhoneTheme`; settery skrz [skinController] použije Nastavení (Fáze 4/5).
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    val skinController: SkinController,
) : ViewModel() {
    val state = skinController.state
}
