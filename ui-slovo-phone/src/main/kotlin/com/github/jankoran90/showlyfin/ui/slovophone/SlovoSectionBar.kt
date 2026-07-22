package com.github.jankoran90.showlyfin.ui.slovophone

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
 * Slovo (EXCISE/SHW-103) — sjednocená horní lišta telefonní sekce (zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmySectionBar]). Jeden tenký pruh: ☰ (otevře menu) +
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
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart, content = content)
        trailing?.invoke(this)
    }
}

/** Titulková varianta — ☰ + název sekce. */
@Composable
fun SlovoSectionBar(
    title: String,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SlovoSectionBar(onMenu = onMenu, modifier = modifier) {
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
