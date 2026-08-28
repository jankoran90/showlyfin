package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage == PAGE_QUEUE) {
        scope.launch { pagerState.animateScrollToPage(PAGE_FILMOTEKA) }
    }

    val titles: @Composable () -> Unit = {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTitle("Filmotéka", pagerState.currentPage == PAGE_FILMOTEKA) {
                scope.launch { pagerState.animateScrollToPage(PAGE_FILMOTEKA) }
            }
            PageTitle("K přehrání", pagerState.currentPage == PAGE_QUEUE) {
                scope.launch { pagerState.animateScrollToPage(PAGE_QUEUE) }
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = modifier.fillMaxSize()) { page ->
        when (page) {
            PAGE_FILMOTEKA -> FilmyFilmotekaScreen(
                onMenu = onMenu,
                onOpenDetail = onOpenDetail,
                onOpenJellyfinDetail = onOpenJellyfinDetail,
                titleContent = titles,
            )
            else -> FilmyQueueScreen(
                onMenu = onMenu,
                onOpenDetail = onOpenDetail,
                titleContent = titles,
            )
        }
    }
}

/** Název stránky v liště — aktivní tučně a barevně, druhý zeslabený (vzor = userova ukázka). */
@Composable
private fun PageTitle(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
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
