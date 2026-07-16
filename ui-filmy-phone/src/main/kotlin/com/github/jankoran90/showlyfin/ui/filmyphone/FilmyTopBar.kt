package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * CELLULOID (SHW-98) Fáze 2 M2.1 — tenká horní lišta appky „Filmy" (vzor audioman [AppTopBar]).
 * ☰ otevírá postranní menu, titulek = název sekce. Ovladače sekce (chipy view-mode / řazení) přijdou
 * v M2.2 jako rolovatelný proužek pod titulkem. Barvy z motivu (AMOLED pozadí).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmyTopBar(
    title: String,
    onMenu: () -> Unit,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Rounded.Menu, contentDescription = "Menu")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
    )
}
