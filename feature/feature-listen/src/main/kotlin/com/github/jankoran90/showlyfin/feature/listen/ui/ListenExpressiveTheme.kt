package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import com.github.jankoran90.showlyfin.core.domain.theme.DarkMode
import com.github.jankoran90.showlyfin.core.domain.theme.ShowlyfinSkin
import com.github.jankoran90.showlyfin.core.domain.theme.SkinPaletteStyle
import com.github.jankoran90.showlyfin.core.ui.theme.rememberSkinColorScheme

/**
 * Téma poslechové sekce — izolovaný wrapper, jediné místo, kde se řeší vzhled „Poslechu".
 *
 * Material 3 **Expressive** (Plan PRISM Fáze 2). Sjednoceno se sdíleným skin enginem:
 * místo 13 natvrdo zadaných barev generuje MaterialKolor celé Expressive schéma z jediného
 * **amber seedu** (`0xFFFFC25C`) — zachovaný brand akcent Poslechu z ORPHEUS. Poslech drží
 * vlastní amber identitu nezávisle na uživatelově skinu (záměr — značková barva sekce).
 */
private val ListenSkin = ShowlyfinSkin(
    seedColor = 0xFFFFC25C,
    style = SkinPaletteStyle.EXPRESSIVE,
    presetId = null,
    darkMode = DarkMode.DARK,
    amoled = false,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListenExpressiveTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = rememberSkinColorScheme(ListenSkin),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
