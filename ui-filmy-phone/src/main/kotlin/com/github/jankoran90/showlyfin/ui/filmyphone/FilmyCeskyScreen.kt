package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem

/**
 * LABYRINT (FLM-04) — stránka „Česky" v liště Filmotéky (user 2026-10-08: „nebo možná rovnou sekci
 * v liště s českým dabingem"). Všechno z vlastní Filmotéky (uložené zdroje + Jellyfin), co má českou
 * nebo slovenskou zvukovou stopu, v řádcích podle hlavního žánru. Stopu pozná server: u uloženého
 * zdroje z `stream.quality.audioLanguage`, u Jellyfinu ze zvukových stop souboru.
 */
@Composable
fun FilmyCeskyScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    vm: CeskyViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        FilmySectionBar(onMenu = onMenu, content = { titleContent() })
        when {
            s.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.radky.isEmpty() -> FilmyEmpty(
                icon = Icons.Rounded.RecordVoiceOver,
                title = if (s.chyba) "Seznam se nenačetl" else "Nic s českým zvukem",
                text = if (s.chyba) "Server teď neodpověděl." else "Ve Filmotéce zatím nic s českou stopou není.",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "souhrn") {
                    KatalogNadpisRadku("S českým zvukem: ${s.celkem}", "Filmotéka podle hlavního žánru — dabing nebo česká stopa.")
                }
                items(s.radky, key = { it.first }) { (zanr, tituly) ->
                    Column {
                        KatalogNadpisRadku("$zanr (${tituly.size})")
                        KatalogPruh(tituly, onOpenDetail)
                    }
                }
            }
        }
    }
}
