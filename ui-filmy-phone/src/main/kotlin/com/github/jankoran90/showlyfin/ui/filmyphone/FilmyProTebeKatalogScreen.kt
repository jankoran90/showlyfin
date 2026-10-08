package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.LocalSourceAvailabilityProvider

/**
 * LABYRINT (FLM-04) F3 — stránka „Pro tebe" v liště Filmotéky, parita s webem (#/protebe):
 * Z Chci vidět co jde hned pustit · Protože se ti líbil X · tvoje podžánry · výběr kurátora.
 * Řádky skládá server (`/api/katalog/protebe`), u každého je věta „proč". Starší LLM sekce
 * „Pro tebe" v bočním menu zůstává beze změny (jiný zdroj doporučení).
 */
@Composable
fun FilmyProTebeKatalogScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenKatalog: (KatalogCil) -> Unit,
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProTebeKatalogViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // uložené zdroje sledujeme reaktivně (backfill může zdroj doplnit, zatímco stránka svítí)
    val ulozene by (LocalSourceAvailabilityProvider.current?.savedKeys
        ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())).collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        FilmyPageBar(onMenu = onMenu, content = { titleContent() })
        Row(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KatalogCip("Jen co jde hned pustit", state.jenHned, vm::prepniJenHned)
            KatalogCip("↻ Zamíchat", false, vm::zamichat)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        "Skládám doporučení… (poprvé to chvíli trvá)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            else -> {
                val radky = state.radky.mapNotNull { r ->
                    val t = if (!state.jenHned) r.tituly
                    else r.tituly.filter { it.zdroj || "tmdb:${it.tmdbId}" in ulozene }
                    if (t.isEmpty()) null else r to t
                }
                if (radky.isEmpty()) {
                    FilmyEmpty(
                        icon = Icons.Rounded.Recommend,
                        title = if (state.chyba) "Doporučení se nenačetla" else "Zatím není co doporučit",
                        text = if (state.jenHned) "Nic z doporučení zatím nemá připravený zdroj."
                        else "Ohodnoť pár filmů (9–10) a přidej něco do Chci vidět.",
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                        items(radky, key = { it.first.id }) { (r, tituly) ->
                            Column {
                                KatalogNadpisRadku(
                                    nazev = r.nazev, proc = r.proc,
                                    onVse = r.odkaz?.let { o ->
                                        val casti = o.split("/", limit = 2)
                                        val cil = KatalogCil(casti[0], casti.getOrElse(1) { "" })
                                        val akce: () -> Unit = { onOpenKatalog(cil) }
                                        akce
                                    },
                                )
                                KatalogPruh(tituly, onOpenDetail)
                            }
                        }
                    }
                }
            }
        }
    }
}
