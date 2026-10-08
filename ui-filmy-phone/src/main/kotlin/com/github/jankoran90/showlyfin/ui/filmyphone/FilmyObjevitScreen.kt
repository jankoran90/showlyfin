package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.KatalogRepository

/**
 * LABYRINT (FLM-04) — stránka „Objevit" v liště Filmotéky (user 2026-10-08: „rád používám ty sekce
 * na hlavní stránce, takže bych tam přidal Objevit"). Mapa žánrů: hlavní žánr otevře celý žánr,
 * štítky pod ním jeho podžánry; nahoře nálady napříč žánry. Parita s webem (#/objevuj).
 */
@Composable
fun FilmyObjevitScreen(
    onMenu: () -> Unit,
    onOpenKatalog: (KatalogCil) -> Unit,
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    vm: ObjevitViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        FilmyPageBar(onMenu = onMenu, content = { titleContent() })
        val mapa = state.mapa
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            mapa == null -> Column(Modifier.fillMaxSize()) {
                FilmyEmpty(
                    icon = Icons.Rounded.Explore,
                    title = "Mapa žánrů se nenačetla",
                    text = "Server teď neodpověděl. Zkus to znovu.",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::load, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Zkusit znovu")
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "nalady") {
                    DlazdiceZanru(
                        nazev = "Nálady a napříč žánry", onNazev = null,
                        stitky = mapa.podzanry.filter { it.nalada }.map { KatalogRepository.Stitek("podzanr", it.id, it.label) },
                        onOpen = onOpenKatalog,
                    )
                }
                items(mapa.hlavni, key = { it.id }) { h ->
                    DlazdiceZanru(
                        nazev = "${h.label} ›",
                        onNazev = { onOpenKatalog(KatalogCil("zanr", h.id)) },
                        stitky = mapa.podzanry.filter { it.parent == h.id }.map { KatalogRepository.Stitek("podzanr", it.id, it.label) },
                        onOpen = onOpenKatalog,
                    )
                }
            }
        }
    }
}

@Composable
private fun DlazdiceZanru(
    nazev: String,
    onNazev: (() -> Unit)?,
    stitky: List<KatalogRepository.Stitek>,
    onOpen: (KatalogCil) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                nazev,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = if (onNazev != null) Modifier.clickable(onClick = onNazev) else Modifier,
            )
            if (stitky.isNotEmpty()) KatalogStitkyFlow(stitky, onOpen, Modifier.padding(top = 4.dp))
        }
    }
}
