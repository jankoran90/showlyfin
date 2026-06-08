package com.github.jankoran90.showlyfin.ui.phone.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.jankoran90.showlyfin.core.domain.theme.ShowlyfinSkin
import com.github.jankoran90.showlyfin.core.ui.theme.rememberSkinColorScheme

val ShowlyfinTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/**
 * Kořenový motiv telefonu — Material 3 **Expressive** (Plan PRISM Fáze 2).
 * Barvy řídí [skin] ze sdíleného engine (`SkinController` → `ThemeViewModel`): seed/preset,
 * režim světlý/tmavý/systém, Material You, AMOLED — vše přes `rememberSkinColorScheme`.
 * Žádné pevné `useDarkTheme=true` ani ručně laděná paleta (to bylo před PRISM).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShowlyfinPhoneTheme(
    skin: ShowlyfinSkin = ShowlyfinSkin.DEFAULT,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = rememberSkinColorScheme(skin),
        motionScheme = MotionScheme.expressive(),
        typography = ShowlyfinTypography,
        content = content,
    )
}
