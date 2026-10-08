package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import kotlinx.coroutines.launch

/**
 * RAMPA (SHW-121) — Filmotéka a fronta „K přehrání" jako DVĚ VODOROVNÉ STRÁNKY.
 *
 * User 2026-08-28 14:10: *„do phone app mi to das do filmoteky ale jako dalsi tab tzn horizontal
 * scroll jako dalsi obrazovka a pozor nahore nepřidávej indikator tab filmoteky uz neni misto, ale
 * až swipnu na tuto sekci tak nějaký Indikator dej kde jsem a ze zpet je filmoteka"*.
 *
 * Řešení bez dalšího patra: **názvy stránek stojí přímo v liště** místo dřívějších chipů os (ty se
 * i s řazením přestěhovaly do panelu ovladačů) — přesně jak vypadala userova druhá ukázka. Aktivní
 * stránka je tučně a barevně, druhá zeslabená, takže „kde jsem" i „zpět je Filmotéka" je vidět
 * naráz a nic se nepřidávalo.
 *
 * Systémové ZPĚT na stránce fronty vrací na Filmotéku (ne ven z appky) — je to sesterská stránka,
 * ne samostatná sekce.
 */
@Composable
fun FilmyFilmotekaPager(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (String) -> Unit = {},
    // LABYRINT (FLM-04): stránka žánru / podžánru / tématu… (zásobník shellu)
    onOpenKatalog: (KatalogCil) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // LABYRINT (user 2026-10-08): „rád používám ty sekce na hlavní stránce, takže bych tam přidal
    // Objevit" → další stránky Objevit, Pro tebe (parita s webem) a Česky (filmy s českým zvukem).
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    // user 2026-10-08: „hlavně ať to není zadrhané, plynule a rychle" — data sousedních stránek se
    // začnou načítat hned při vstupu na Filmotéku (tytéž ViewModely si pak stránky jen vezmou), takže
    // po přejetí už je obsah připravený a nečeká se na server.
    androidx.hilt.navigation.compose.hiltViewModel<ObjevitViewModel>()
    androidx.hilt.navigation.compose.hiltViewModel<ProTebeKatalogViewModel>()
    androidx.hilt.navigation.compose.hiltViewModel<CeskyViewModel>()

    BackHandler(enabled = pagerState.currentPage != PAGE_FILMOTEKA) {
        scope.launch { pagerState.animateScrollToPage(PAGE_FILMOTEKA) }
    }

    // LABYRINT (user 2026-10-08: „má se tahat jen obsah a ne vše") — JEDNA pevná lišta nad pagerem:
    // ☰ + názvy stránek + akce aktivní stránky vpravo. Stránky si lištu nekreslí (LocalPagerBar),
    // jen sem podají své akce. Přejíždí se tak opravdu jen obsah.
    val akce = remember { mutableStateMapOf<Int, @Composable RowScope.() -> Unit>() }
    val titulky = listOf(
        PAGE_FILMOTEKA to "Filmotéka", PAGE_QUEUE to "K přehrání", PAGE_OBJEVIT to "Objevit",
        PAGE_PROTEBE to "Pro tebe", PAGE_CESKY to "Česky",
    )
    val scroll = rememberScrollState()
    val pozice = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current
    // aktivní název vždy na očích (u pěti stránek se do lišty nevejdou všechny)
    LaunchedEffect(pagerState.currentPage) {
        val x = pozice[pagerState.currentPage] ?: return@LaunchedEffect
        scroll.animateScrollTo((x - with(density) { 24.dp.roundToPx() }).coerceAtLeast(0))
    }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = { akce[pagerState.currentPage]?.invoke(this) },
        ) {
            Row(
                Modifier.horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                titulky.forEach { (page, nazev) ->
                    PageTitle(
                        nazev, pagerState.currentPage == page,
                        Modifier.onGloballyPositioned { pozice[page] = it.positionInParent().x.toInt() },
                    ) { scope.launch { pagerState.animateScrollToPage(page) } }
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val hostitel: (@Composable RowScope.() -> Unit) -> Unit = { a -> akce[page] = a }
            CompositionLocalProvider(LocalPagerBar provides hostitel) {
                when (page) {
                    PAGE_FILMOTEKA -> FilmyFilmotekaScreen(
                        onMenu = onMenu,
                        onOpenDetail = onOpenDetail,
                        onOpenJellyfinDetail = onOpenJellyfinDetail,
                    )
                    PAGE_QUEUE -> FilmyQueueScreen(
                        onMenu = onMenu,
                        onOpenDetail = onOpenDetail,
                        titleContent = {},
                    )
                    PAGE_OBJEVIT -> FilmyObjevitScreen(
                        onMenu = onMenu,
                        onOpenKatalog = onOpenKatalog,
                        titleContent = {},
                    )
                    PAGE_PROTEBE -> FilmyProTebeKatalogScreen(
                        onMenu = onMenu,
                        onOpenDetail = onOpenDetail,
                        onOpenKatalog = onOpenKatalog,
                        titleContent = {},
                    )
                    else -> FilmyCeskyScreen(
                        onMenu = onMenu,
                        onOpenDetail = onOpenDetail,
                        titleContent = {},
                    )
                }
            }
        }
    }
}

/** Název stránky v liště — aktivní tučně a barevně, druhý zeslabený (vzor = userova ukázka). */
@Composable
private fun PageTitle(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(end = 4.dp)
            // Bez vlnky — ripple přes text v liště ruší (klik je jen zkratka k přejetí prstem).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

private const val PAGE_FILMOTEKA = 0
private const val PAGE_QUEUE = 1
private const val PAGE_OBJEVIT = 2
private const val PAGE_PROTEBE = 3
private const val PAGE_CESKY = 4
private const val PAGE_COUNT = 5
