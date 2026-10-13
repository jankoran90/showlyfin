package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.EpisodeUi
import com.github.jankoran90.showlyfin.core.ui.PhoneEpisodeRow
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastItemKey
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastPrefs
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastsViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.podcastShelves

/**
 * AMFITEÁTR (FLM-05) F4 — sekce Podcasty v telefonu Filmů (user 2026-10-13: „odmakej to i pro telefon").
 * Stejná data i pravidla jako TV ([podcastShelves] nad [PodcastsViewModel]); telefonní transpozice jako
 * domov: řada = tab, obsah řady = svislý seznam dílů. Dlouhý stisk dílu YouTube = „Přeložit do češtiny".
 */
@Composable
fun FilmyPodcastsScreen(
    onMenu: () -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
    onPlay: (PodcastsViewModel.PlayTarget) -> Unit,
    onManageSources: () -> Unit = {},
    vm: PodcastsViewModel = hiltViewModel(),
) {
    val manage: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (vm.canFollow()) {
            androidx.compose.material3.IconButton(onClick = onManageSources) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.AddCircle,
                    contentDescription = "Spravovat zdroje",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onEnter() }
    var confirmFor by remember { mutableStateOf<Pair<PodcastSource, SourceEpisode>?>(null) }

    val prefs = LocalContext.current.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)
    val rowPrefs = remember { PodcastPrefs.rows(prefs) }
    val rails = remember(state, marks, translated, translating, rowPrefs, watched) {
        val shelves = podcastShelves(state, marks, translated, translating, rowPrefs, watched)
        buildList {
            if (shelves.continueItems.isNotEmpty()) add(FilmyRail("podcasty-pokracovat", "Další díly", HomeCardStyle.LANDSCAPE, shelves.continueItems))
            if (shelves.newItems.isNotEmpty()) add(FilmyRail("podcasty-nove", "Nové díly", HomeCardStyle.LANDSCAPE, shelves.newItems))
            if (shelves.channelItems.isNotEmpty()) add(FilmyRail("podcasty-kanaly", "Kanály a pořady", HomeCardStyle.LANDSCAPE, shelves.channelItems))
        }
    }

    confirmFor?.let { (src, ep) ->
        FilmyTranslateConfirmDialog(
            title = ep.title,
            alreadyTranslated = "yt:${ep.id}" in translated,
            onConfirm = { vm.translate(src, ep); confirmFor = null },
            onDismiss = { confirmFor = null },
        )
    }

    if (rails.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            FilmySectionBar(onMenu = onMenu, trailing = manage) {
                Text("Pořady", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                when {
                    state.loading -> "Načítám podcasty…"
                    state.error != null -> state.error!!
                    else -> "Zatím tu nejsou žádné podcasty s videem. Kanály YouTube, pořady ČT a video podcasty " +
                        "přidáš tlačítkem ⊕ vpravo nahoře (nebo ve Slovu — zdroje jsou společné)."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val onItem: (HomeRowItem) -> Unit = { item ->
        when (val ref = PodcastItemKey.parse(item.key)) {
            is PodcastItemKey.Source -> vm.sourceOf(ref.sourceKey)?.let(onOpenSource)
            is PodcastItemKey.Episode -> {
                val src = vm.sourceOf(ref.sourceKey)
                val ep = vm.episodeOf(ref.sourceKey, ref.episodeId)
                if (src != null && ep != null) vm.playTarget(src, ep)?.let(onPlay)
            }
            null -> Unit
        }
    }
    FilmyHomeTabbed(
        rails = rails,
        onItemClick = onItem,
        onMenu = onMenu,
        barTrailing = manage,
        itemRow = { item ->
            val ref = PodcastItemKey.parse(item.key) as? PodcastItemKey.Episode
            val src = ref?.let { vm.sourceOf(it.sourceKey) }
            val ep = ref?.let { vm.episodeOf(it.sourceKey, it.episodeId) }
            FilmyPodcastRow(
                item = item,
                onClick = { onItem(item) },
                onLongClick = if (src != null && ep != null && vm.canTranslate(src)) {
                    { confirmFor = src to ep }
                } else null,
            )
        },
    )
}

/** Řádek dílu / kanálu = kanonický [PhoneEpisodeRow] (náhled 16:9, průběh), jen bez číslování S00E00. */
@Composable
internal fun FilmyPodcastRow(item: HomeRowItem, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    PhoneEpisodeRow(
        episode = EpisodeUi(
            key = item.key,
            seasonNumber = 0,
            episodeNumber = 0,
            title = item.title,
            imageUrl = item.landscapeUrl ?: item.posterUrl,
            meta = item.subtitle,
            progressPct = item.progressPct,
            watched = item.watched,
            numbered = false,
        ),
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** „Přeložit do češtiny?" — překlad běží na pozadí, průběh ukáže proužek dole a notifikace. */
@Composable
internal fun FilmyTranslateConfirmDialog(
    title: String,
    alreadyTranslated: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (alreadyTranslated) "Český překlad je hotový" else "Přeložit do češtiny?") },
        text = {
            Text(
                if (alreadyTranslated) "„$title“ už má české titulky. Při přehrání se nasadí samy."
                else "„$title“ se přeloží na pozadí. Průběh a odhad času uvidíš dole na obrazovce a v notifikaci, " +
                    "mezitím můžeš dělat cokoli jiného. Překlad čerpá kvótu mozku.",
            )
        },
        confirmButton = { if (!alreadyTranslated) TextButton(onClick = onConfirm) { Text("Přeložit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (alreadyTranslated) "Zavřít" else "Zrušit") } },
    )
}
