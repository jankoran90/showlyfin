package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.data.uploader.KatalogRepository

/**
 * LABYRINT (FLM-04) — sdílené kousky Objevit / stránky žánru / Pro tebe v appce Filmy.
 * Karty = sdílená [MediaCard] (odznak zdroje, fronty a hodnocení dodávají providery shellu),
 * takže titul z katalogu vypadá stejně jako kdekoli jinde v appce.
 */

/** Titul z katalogu → stub [MediaItem] pro kartu i sdílený DetailScreen (ten si ho dohydratuje). */
fun KatalogRepository.Titul.toMediaItem(): MediaItem = MediaItem(
    traktId = 0L, tmdbId = tmdbId, imdbId = null, title = nazev, year = rok,
    overview = popis.ifBlank { null }, rating = hodnoceni, genres = null,
    type = if (isShow) MediaType.SHOW else MediaType.MOVIE, posterPath = posterPath,
)

/** Cíl štítku na stránce katalogu („podzanr", „cerny-humor"). */
data class KatalogCil(val druh: String, val arg: String)

@Composable
fun KatalogCip(text: String, aktivni: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = aktivni,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Klikací štítky (žánr, podžánr, rok, země, téma). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KatalogStitkyFlow(
    stitky: List<KatalogRepository.Stitek>,
    onOpen: (KatalogCil) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        stitky.forEach { s ->
            val podzanr = s.druh == "podzanr"
            SuggestionChip(
                onClick = { onOpen(KatalogCil(s.druh, s.id)) },
                label = {
                    Text(
                        s.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = if (podzanr) MaterialTheme.colorScheme.primary
                    else if (s.druh == "tema") MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

/** Štítky pod názvem na kartě filmu (slot DetailScreen). Profil titulu dotáhne server. */
@Composable
fun FilmyKatalogStitkyKarty(klic: String, onOpen: (KatalogCil) -> Unit, vm: KatalogStitkyViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    if (!KatalogPrefs.bool(ctx, KatalogPrefs.STITKY_KARTA)) return
    val vsechny by vm.stitky.collectAsStateWithLifecycle()
    LaunchedEffect(klic) { vm.nacti(klic) }
    val stitky = vsechny[klic].orEmpty()
    if (stitky.isNotEmpty()) KatalogStitkyFlow(stitky, onOpen, Modifier.padding(top = 6.dp))
}

/** Nadpis řádku + věta „proč" + odkaz „vše ›". */
@Composable
fun KatalogNadpisRadku(nazev: String, proc: String? = null, onVse: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                nazev, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f, fill = false),
            )
            if (onVse != null) {
                Text(
                    "  vše ›", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onVse),
                )
            }
        }
        if (!proc.isNullOrBlank()) {
            Text(proc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Vodorovný pruh karet z katalogu. */
@Composable
fun KatalogPruh(
    tituly: List<KatalogRepository.Titul>,
    onOpenDetail: (MediaItem) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth()) {
        items(tituly, key = { it.klic }) { t ->
            Box(Modifier.padding(horizontal = 4.dp).width(118.dp).height(215.dp)) {
                val mi = t.toMediaItem()
                MediaCard(item = mi, onClick = { onOpenDetail(mi) }, watched = t.videno)
            }
        }
    }
}

/** Hledat: řádek štítků „žánry a témata" k dotazu → stránka katalogu. */
@Composable
fun FilmyKatalogHledatStitky(query: String, onOpen: (KatalogCil) -> Unit, vm: KatalogHledatViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    if (!KatalogPrefs.bool(ctx, KatalogPrefs.HLEDAT_STITKY)) return
    LaunchedEffect(query) { vm.dotaz(query) }
    val stitky by vm.stitky.collectAsStateWithLifecycle()
    if (stitky.isEmpty() || query.isBlank()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(stitky, key = { "${it.druh}:${it.id}" }) { s ->
            SuggestionChip(
                onClick = { onOpen(KatalogCil(s.druh, s.id)) },
                label = { Text(s.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = if (s.druh == "tema") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
