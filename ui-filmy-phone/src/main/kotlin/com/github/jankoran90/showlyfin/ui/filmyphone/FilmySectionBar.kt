package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * CELLULOID (SHW-98) M2.5 — sjednocená horní lišta telefonní sekce appky „Filmy".
 *
 * Jeden tenký pruh: ☰ (otevře menu) + ovladače sekce ([content] = taby domova / chipy Filmotéky / titulek)
 * + volitelné akce vpravo ([trailing] = přepínač zobrazení). Nahrazuje dřívější dvojitou lištu
 * (Scaffold TopAppBar s názvem sekce + samostatný pruh ovladačů) → obsah dostane víc místa (minimal chrome,
 * vize M2.2b). Barvy z motivu (AMOLED pozadí). Princip usera 2026-07-17: ovladače v liště KAŽDÉ sekce.
 */
@Composable
fun FilmySectionBar(
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
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart, content = content)
        trailing?.invoke(this)
    }
}

/** Titulková varianta pro sekce bez vlastních ovladačů (Nastavení, placeholdery) — ☰ + název sekce. */
@Composable
fun FilmySectionBar(
    title: String,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilmySectionBar(onMenu = onMenu, modifier = modifier) {
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


/**
 * LABYRINT (user 2026-10-08: „přeměny sekcí fungují divně, má se tahat jen obsah a ne vše") — stránky
 * v pageru Filmotéky NEKRESLÍ vlastní lištu; pager drží jednu pevnou lištu nahoře a stránka mu sem jen
 * podá své akce vpravo (ikona ovladačů, přepínač zobrazení…). null = stránka stojí samostatně a lištu
 * si kreslí sama (Pro tebe v draweru apod.).
 */
val LocalPagerBar = androidx.compose.runtime.staticCompositionLocalOf<((@Composable RowScope.() -> Unit) -> Unit)?> { null }

/** Lišta stránky: v pageru jen předá akce hostiteli, jinak ji nakreslí sama (☰ + [content] + [trailing]). */
@Composable
fun FilmyPageBar(
    onMenu: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalPagerBar.current
    if (host != null) {
        androidx.compose.runtime.SideEffect { host(trailing ?: {}) }
    } else {
        FilmySectionBar(onMenu = onMenu, trailing = trailing, content = content)
    }
}
