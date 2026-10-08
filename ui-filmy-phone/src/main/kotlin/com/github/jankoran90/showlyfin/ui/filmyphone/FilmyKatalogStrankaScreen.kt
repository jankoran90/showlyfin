package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import kotlinx.coroutines.launch

private val RAZENI = listOf("hodnoceni" to "Nejlépe hodnocené", "popularita" to "Populární", "novinky" to "Novinky", "klenoty" to "Skryté klenoty")
private val DEKADY = listOf(null to "Všechna léta", 2020 to "2020+", 2010 to "2010–19", 2000 to "2000–09",
    1990 to "90. léta", 1980 to "80. léta", 1970 to "70. léta", 1960 to "60. léta", 0 to "Starší")

/**
 * LABYRINT (FLM-04) F2 — stránka žánru / podžánru / tématu / roku / země (parita s webem):
 * Máš k dispozici (vlastní Filmotéka) · pruhy z celého katalogu (Nejlépe hodnocené, Skryté klenoty,
 * Novinky, České, Seriály) · Procházet vše s řazením, Filmy/Seriály, dekádou, „Jen české" a
 * nekonečným listováním. Otevírá se z Objevit, ze štítků na kartě filmu a z hledání (zásobník shellu).
 */
@Composable
fun FilmyKatalogStrankaScreen(
    cil: KatalogCil,
    onBack: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenKatalog: (KatalogCil) -> Unit,
    modifier: Modifier = Modifier,
    vm: KatalogStrankaViewModel = hiltViewModel(key = "katalog:${cil.druh}/${cil.arg}"),
) {
    LaunchedEffect(cil) { vm.open(cil.druh, cil.arg) }
    val s by vm.state.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // index první položky „Procházet vše" v mřížce (kvůli „vše ›" u pruhu → sjeď k výpisu)
    val zacatekVse = 3 + (if (s.vlastni.isNotEmpty()) 1 else 0) + s.pruhy.size

    // nekonečné listování: blízko konce mřížky načti další stránku
    val blizkoKonce by remember {
        derivedStateOf {
            val info = grid.layoutInfo
            val posledni = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && posledni >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { blizkoKonce }.collect { if (it) vm.nactiDalsi() }
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět") }
            Column(Modifier.weight(1f)) {
                s.rodic?.let { (id, label) ->
                    Text(
                        "$label ›", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onOpenKatalog(KatalogCil("zanr", id)) },
                    )
                }
                Text(s.nadpis, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            state = grid,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            plne("sourozenci") {
                if (s.sourozenci.isNotEmpty()) KatalogStitkyFlow(s.sourozenci, onOpenKatalog, Modifier.padding(horizontal = 8.dp))
            }
            if (s.vlastni.isNotEmpty()) plne("vlastni") {
                Column {
                    val vlastni = if (s.jenCz) s.vlastni.filter { it.cz } else s.vlastni
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { KatalogNadpisRadku("Máš k dispozici (${vlastni.size})") }
                        // user 2026-10-08: přepínač „obsahuje českou stopu"
                        Box(Modifier.padding(end = 12.dp, top = 8.dp)) { KatalogCip("CZ zvuk", s.jenCz, vm::prepniCz) }
                    }
                    if (vlastni.isEmpty()) {
                        Text(
                            "Nic z toho nemá českou stopu.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else KatalogPruh(vlastni, onOpenDetail)
                }
            }
            s.pruhy.forEach { p ->
                plne("pruh-${p.nazev}") {
                    Column {
                        KatalogNadpisRadku(
                            p.nazev,
                            onVse = p.razeni?.let { r -> { vm.vseJako(r); scope.launch { grid.animateScrollToItem(zacatekVse) } } },
                        )
                        KatalogPruh(p.tituly, onOpenDetail)
                    }
                }
            }
            plne("vse-nadpis") { KatalogNadpisRadku("Procházet vše") }
            plne("vse-ovladani") {
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KatalogCip("Filmy", !s.serialy) { vm.setSerialy(false) }
                        KatalogCip("Seriály", s.serialy) { vm.setSerialy(true) }
                        if (cil.druh != "zeme") KatalogCip("Jen české", s.ceske) { vm.setCeske(!s.ceske) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RAZENI.forEach { (k, l) -> KatalogCip(l, s.razeni == k) { vm.setRazeni(k) } }
                    }
                    if (cil.druh != "rok") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DEKADY.forEach { (k, l) -> KatalogCip(l, s.dekada == k) { vm.setDekada(k) } }
                        }
                    }
                }
            }
            items(s.vse, key = { "vse-${it.klic}" }) { t ->
                Box(Modifier.height(215.dp)) {
                    val mi = t.toMediaItem()
                    MediaCard(item = mi, onClick = { onOpenDetail(mi) }, watched = t.videno)
                }
            }
            plne("konec") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        s.nacitam -> CircularProgressIndicator()
                        s.vse.isEmpty() -> Text(
                            if (s.serialy) "Seriály v tomhle žánru TMDB nevede." else "Nic se nenašlo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Položka mřížky přes celou šířku. */
private fun LazyGridScope.plne(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
