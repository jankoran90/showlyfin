package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.feature.listen.ListenMode
import com.github.jankoran90.showlyfin.feature.listen.ListenUiState
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import kotlinx.coroutines.launch

// Vyčleněno z ListenScreen (2026-10-13, anti-monolit): strana Sledované.

/**
 * AGORA-TABS: Sledované = grid vlastních zdrojů + ABS podcastů. User (2026-08-16 18:23, „Poslech
 * čisté jako Domů") — samostatná swipe strana bez filtru typu zdroje (ten dřív žil ve sdíleném
 * filtr sheetu s Timeline/Objev — na téhle čisté stránce se neukazuje; `sourceType` volající strana
 * nechá na "all").
 */
@Composable
fun FollowingContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenPodcast: (String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    onOpenMerged: (groupId: String, title: String) -> Unit,
    downloadCount: Int,
    sourceType: String,
) {
    // WEFT (SHW-75/W5): per-profil skrytí pořadů ve Sledovaných + akční sheet při dlouhém stisku karty.
    val cfg by viewModel.profileConfig.collectAsStateWithLifecycle()
    val hiddenFollowing = cfg.hiddenFollowingSourceKeys
    var actionCard by remember { mutableStateOf<LibraryCard?>(null) }
    // User (2026-08-15) — dlouhý stisk na ABS podcast (jen Dospělý) → "Zobrazit/Skrýt dětem".
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    // PROFIL (2026-08-16, „zdroje per profil + sdílení") — vidím jen svoje vlastní zdroje + co mi kdo
    // sdílel. Legacy zdroje (addedBy == null, vznikly PŘED touhle featurou) zůstávají viditelné všem.
    val myUuid = activeProfile?.profileUuid
    val visibleSources = remember(state.customSources, myUuid, cfg.sharedSourceKeys) {
        // Dětský profil sem nechodí (má KidsListenContent) → isKids = false.
        state.customSources.filter { it.isVisibleTo(myUuid, isKids = false, cfg.sharedSourceKeys, emptySet()) }
    }
    // Filtr typu zdroje (z filtru): all|rss|youtube — aplikuje se na vlastní zdroje. ABS podcasty
    // ukazujeme jen u „Vše" nebo „Podcasty" (jsou to RSS-like pořady, ne YouTube).
    val sources = visibleSources.filter { sourceType == "all" || it.type == sourceType }
    val showAbs = sourceType == "all" || sourceType == "rss"

    // TWINE (SHW-74 / plán F7): slinkované zdroje (audio+video = týž pořad) → 1 sloučená karta.
    val links by viewModel.sourceLinks.collectAsStateWithLifecycle()
    var linkFor by remember { mutableStateOf<com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource?>(null) }
    // SLOVO-KIDS-EPISODE — dlouhý stisk i na vlastních zdrojích (Plain/Merged) → "Zobrazit/Skrýt dětem".
    val kidsVisibleSources by viewModel.kidsVisibleSourceKeys.collectAsStateWithLifecycle()
    val byKey = remember(visibleSources) { visibleSources.associateBy { viewModel.sourceKey(it) } }
    val linkedKeys = remember(links) { links.flatMap { it.members }.toSet() }
    // Samostatné karty = filtrované zdroje, které nejsou v žádné skupině.
    val plainSources = sources.filter { viewModel.sourceKey(it) !in linkedKeys }

    // WEFT (SHW-75/W3+W5): sloučené + samostatné + ABS karty do JEDNOHO abecedně řazeného gridu
    // (dřív sloučené vždy první → nepůsobilo „podle abecedy"), odfiltrované o per-profil skryté
    // ve Sledovaných (W5). Sloučená karta zmizí, až když jsou skryté VŠECHNY její verze.
    val libraryCards = remember(links, byKey, plainSources, state.podcasts, showAbs, sourceType, hiddenFollowing) {
        buildList<LibraryCard> {
            links.forEach { g ->
                val members = g.members.mapNotNull { byKey[it] }
                if (members.isEmpty()) return@forEach
                if (sourceType != "all" && members.none { it.type == sourceType }) return@forEach
                add(LibraryCard.Merged(g.id, g.title ?: members.first().title, g.thumbnail ?: members.firstNotNullOfOrNull { it.thumbnail }, g.members))
            }
            plainSources.forEach { add(LibraryCard.Plain(it, viewModel.sourceKey(it))) }
            if (showAbs) state.podcasts.forEach { add(LibraryCard.Abs(it)) }
        }
            .filter { card -> card.hideKeys.any { it !in hiddenFollowing } }
            .sortedBy { it.sortTitle.lowercase(java.util.Locale("cs")) }
    }
    val hasContent = libraryCards.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        when {
            state.isLoading && !hasContent ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            state.isOffline && !hasContent ->
                CenteredMessage(
                    if (downloadCount > 0) {
                        "Offline — online seznam podcastů není dostupný.\nStažené epizody najdeš nahoře."
                    } else {
                        "Jsi offline a nemáš žádné stažené epizody."
                    },
                )

            state.error != null && !hasContent -> CenteredMessage(state.error)

            else -> {
                if (showAbs && state.podcastLibraries.size > 1) {
                    LibraryChips(
                        libraries = state.podcastLibraries.map { it.id to it.name },
                        selectedId = state.selectedPodcastLibraryId,
                        onSelect = viewModel::selectPodcastLibrary,
                    )
                }
                if (!hasContent && !state.isLoading) {
                    CenteredMessage("Zatím nesleduješ žádné podcasty.\nPřidej zdroje v záložce Objev.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // WEFT (SHW-75/W3): jeden abecedně řazený grid (sloučené + samostatné + ABS).
                        items(libraryCards, key = { it.itemKey }) { card ->
                            when (card) {
                                is LibraryCard.Merged ->
                                    MergedSourceCard(
                                        title = card.title,
                                        thumbnail = card.thumbnail,
                                        onClick = { onOpenMerged(card.groupId, card.title) },
                                        onLongClick = { actionCard = card },
                                    )
                                is LibraryCard.Plain ->
                                    SourceCard(
                                        source = card.source,
                                        onClick = { onOpenSource(card.source) },
                                        onLongClick = { actionCard = card },
                                    )
                                is LibraryCard.Abs ->
                                    PodcastCard(
                                        podcast = card.podcast,
                                        onClick = { onOpenPodcast(card.podcast.id) },
                                        onLongClick = { actionCard = card },
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    // WEFT (SHW-75/W5): dlouhý stisk karty → akce (Propojit / Skrýt ve Sledovaných / Skrýt na časové ose).
    // User (2026-08-15): u ABS podcastu navíc (jen Dospělý) "Zobrazit/Skrýt dětem" — zkratka k témuž
    // hiddenPodcastIds, co jinak nastavuje sekce Profil → Podcasty pro Děti (rychlejší z gridu).
    // PROFIL (2026-08-16) — ostatní dospělí profilové → "Sdílet s…" akce, jedna na profil.
    val otherAdultProfiles by viewModel.otherAdultProfiles.collectAsStateWithLifecycle()
    actionCard?.let { card ->
        val absPodcastId = (card as? LibraryCard.Abs)?.podcast?.id
        val shareActions = if (card is LibraryCard.Plain || card is LibraryCard.Merged) {
            otherAdultProfiles.map { target ->
                val shared = viewModel.isSourceSharedWith(card.hideKeys, target)
                ListenEpisodeAction(
                    if (shared) Icons.Default.Visibility else Icons.Default.Share,
                    if (shared) "Přestat sdílet s ${target.name}" else "Sdílet s ${target.name}",
                ) { viewModel.setSourceSharedWith(card.hideKeys, target.id, !shared) }
            }
        } else emptyList()
        // User 2026-10-13 („ať je to stejné nehledě na typ zdroje, i ovládání") — stejné volby stažení jako u dětí.
        val shelfActions: com.github.jankoran90.showlyfin.feature.listen.KidsShelfActionsViewModel = hiltViewModel()
        val downloadTarget = when (card) {
            is LibraryCard.Plain -> SourceDownloadTarget(card.source, setOf(card.key), null, card.source.title)
            is LibraryCard.Merged -> SourceDownloadTarget(
                card.members.mapNotNull { k -> state.customSources.firstOrNull { it.key == k } }.sortedBy { it.type == "youtube" }.firstOrNull(),
                card.members.toSet(), null, card.title,
            )
            is LibraryCard.Abs -> null
        }
        val (downloadInfo, downloadActions) = downloadTarget
            ?.let { sourceDownloadActions(it, state.isOffline, shelfActions) } ?: ((null as String?) to emptyList<ListenEpisodeAction>())
        ListenEpisodeActionSheet(
            title = card.sortTitle,
            infoLine = if (card is LibraryCard.Plain || card is LibraryCard.Merged) {
                val owner = card.hideKeys.firstNotNullOfOrNull { viewModel.ownerOfSourceKey(it) }
                listOfNotNull(
                    viewModel.ownershipInfoLine(owner, otherAdultProfiles.filter { viewModel.isSourceSharedWith(card.hideKeys, it) }),
                    downloadInfo,
                ).joinToString(" · ")
            } else null,
            actions = downloadActions + listOfNotNull(
                (card as? LibraryCard.Plain)?.let { c ->
                    ListenEpisodeAction(Icons.Default.Link, "Propojit s jinou verzí (audio + video)") {
                        linkFor = c.source
                    }
                },
                if (absPodcastId != null && activeProfile?.isAdmin == true) {
                    val visibleToKids = "abs:$absPodcastId" in kidsVisibleSources
                    ListenEpisodeAction(
                        if (visibleToKids) Icons.Default.ChildCare else Icons.Default.Visibility,
                        if (visibleToKids) "Skrýt dětem" else "Zobrazit dětem",
                    ) { viewModel.setPodcastVisibleForKids(absPodcastId, !visibleToKids) }
                } else null,
                // SLOVO-KIDS-EPISODE — totéž pro vlastní zdroje (Plain/Merged), whitelist místo blacklistu.
                if ((card is LibraryCard.Plain || card is LibraryCard.Merged) && activeProfile?.isAdmin == true) {
                    val visibleToKids = card.hideKeys.any { it in kidsVisibleSources }
                    ListenEpisodeAction(
                        if (visibleToKids) Icons.Default.ChildCare else Icons.Default.Visibility,
                        if (visibleToKids) "Skrýt dětem" else "Zobrazit dětem",
                    ) { viewModel.setSourceVisibleForKids(card.hideKeys, !visibleToKids) }
                } else null,
            ) + shareActions + listOfNotNull(
                ListenEpisodeAction(Icons.Default.VisibilityOff, "Skrýt ve Sledovaných") {
                    viewModel.setHidden(card.hideKeys, timeline = false, hidden = true)
                },
                ListenEpisodeAction(Icons.Default.VisibilityOff, "Skrýt na časové ose") {
                    viewModel.setHidden(card.hideKeys, timeline = true, hidden = true)
                },
            ),
            onDismiss = { actionCard = null },
        )
    }

    // TWINE: dlouhý stisk karty → vyber druhou verzi pořadu k propojení (auto-návrh nahoře, potvrdí user).
    linkFor?.let { src ->
        SourceLinkSheet(
            source = src,
            candidates = viewModel.linkCandidates(src),
            suggested = viewModel.suggestLinkMatch(src),
            onLink = { target -> viewModel.linkSources(src, target) },
            onDismiss = { linkFor = null },
        )
    }
}
