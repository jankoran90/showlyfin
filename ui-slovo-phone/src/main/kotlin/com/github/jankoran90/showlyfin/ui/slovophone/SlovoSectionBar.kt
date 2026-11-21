package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Rodičovská správa (user 2026-11-19 15:57: „sidebar bych mu tam znepřístupnil … zbytečně odvádí
 * pozornost") — v dětském profilu lišta nemá avatar. Rodič nabídku otevře podržením levého horního rohu 3 s
 * (odtud Profil s PINem → zpět na dospělého).
 */
val LocalSlovoMenuLocked = staticCompositionLocalOf { false }

private const val MENU_UNLOCK_HOLD_MS = 3_000L

/** Jméno aktivního profilu pro avatar v liště (AMPHORA F1) — dodává shell. */
val LocalSlovoProfileName = staticCompositionLocalOf<String?> { null }

/** Kulatý avatar s počátečním písmenem profilu (bez jména ikona osoby). */
@Composable
fun SlovoAvatar(name: String?, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
        if (initial != null) {
            Text(
                initial.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Slovo (EXCISE/SHW-103) — sjednocená horní lišta telefonní sekce (zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmySectionBar]). Jeden tenký pruh: avatar (otevře Víc) +
 * ovladače/titulek sekce ([content]) + volitelné akce vpravo ([trailing]). Barvy z motivu (AMOLED).
 */
@Composable
fun SlovoSectionBar(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalSlovoMenuLocked.current) {
            Box(
                Modifier.size(48.dp).pointerInput(onMenu) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // null jen při vypršení (puštění i zrušení gesta vrátí true) — revize 2026-11-19.
                        val endedEarly = withTimeoutOrNull(MENU_UNLOCK_HOLD_MS) { waitForUpOrCancellation(); true }
                        if (endedEarly == null) onMenu()
                    }
                },
            )
        } else {
            // AMPHORA F1 (KO1): místo ☰ avatar profilu → nabídka Víc (profil, Objevit, Zdroje, …).
            IconButton(onClick = onMenu) { SlovoAvatar(LocalSlovoProfileName.current) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart, content = content)
        trailing?.invoke(this)
    }
}

/**
 * Titulková varianta — ☰ + název sekce (+ volitelné [trailing], např. přepínač Audioknihy/Podcasty
 * u Poslechu — user 2026-08-16 13:43 „šoupni tlačítka nahoru vedle nadpisu Poslech").
 */
@Composable
fun SlovoSectionBar(
    title: String,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    SlovoSectionBar(onMenu = onMenu, modifier = modifier, trailing = trailing) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
