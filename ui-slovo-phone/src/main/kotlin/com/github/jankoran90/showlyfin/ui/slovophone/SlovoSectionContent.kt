package com.github.jankoran90.showlyfin.ui.slovophone

import com.github.jankoran90.showlyfin.feature.listen.bookmarks.BookmarkOpeners
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.BookmarksScreen
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AUDIOTEKA_SHELF_URL
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AudiotekaCatalogScreen
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AudiotekaPrefs
import com.github.jankoran90.showlyfin.feature.listen.ui.BooksContent
import com.github.jankoran90.showlyfin.feature.listen.ui.DownloadsScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.FollowingContent
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeRow
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.LibraryPrefs
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDiscoveryScreen
import com.github.jankoran90.showlyfin.feature.listen.search.UnifiedSearchScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.QueueScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.SourceManagerScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.TimelinePage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * AMPHORA (SHW-124) F1 — obsah jedné sekce (místa lišty nebo položky z Víc). Vyčleněno ze
 * [SlovoPhoneShell] (anti-monolit). Pořady = Nové (dřívější Timeline) | Sledované jako dvě přejížděcí
 * strany; Knihovna = Knihy | Pořady | Stažené; Hledat = jedno hledání pro všechno (F3).
 */
@Composable
internal fun SlovoSectionContent(
    section: SlovoSection,
    isKidsProfile: Boolean,
    listenVm: ListenViewModel,
    podcastLinkLookup: PodcastLinkLookupViewModel,
    onMenu: () -> Unit,
    onPush: (SlovoDetailEntry) -> Unit,
    onSelectSection: (SlovoSection) -> Unit,
    expandMiniPlayer: () -> Unit,
) {
    val listenState by listenVm.uiState.collectAsStateWithLifecycle()
    val podcastDownloads by listenVm.offlinePodcasts.collectAsStateWithLifecycle()
    val onOpenBook: (String) -> Unit = { onPush(SlovoDetailEntry.AudiobookDetail(it)) }
    val onOpenSourceEpisode: (String, String, String, String) -> Unit = { type, ref, srcTitle, epKey ->
        onPush(linkedOrPlain(podcastLinkLookup, type, ref, srcTitle, epKey))
    }
    // ADAPT (2026-09-04): sdílený video launcher (YouTube/ČT externí URL) — AI titulky + 360p záložka,
    // parita s [SlovoDetailHost.playYoutubeVideo].
    val playExternalVideo: (url: String, title: String, poster: String?) -> Unit = { url, title, poster ->
        onPush(
            SlovoDetailEntry.VideoPlayer(
                externalUrl = url, title = title, posterUrl = poster,
                subtitleQuery = youtubeSubtitleQuery(url, title),
                fallbackUrl = youtube360Fallback(url),
            ),
        )
    }
    val playJfVideo: (jfItemId: String, title: String, resumeKey: String) -> Unit = { jfItemId, title, resumeKey ->
        onPush(SlovoDetailEntry.VideoPlayer(itemId = jfItemId, title = title, resumeKey = resumeKey))
    }

    when (section) {
        SlovoSection.DOMU -> SlovoSectionScaffold("Slovo", onMenu) {
            if (isKidsProfile) {
                HomeScreen(onOpenBook = onOpenBook, onOpenSourceEpisode = onOpenSourceEpisode, modifier = Modifier.fillMaxSize())
            } else {
                HomeScreen(
                    onOpenBook = onOpenBook,
                    onOpenSourceEpisode = onOpenSourceEpisode,
                    onPlayVideo = playExternalVideo,
                    onPlayJfVideo = playJfVideo,
                    modifier = Modifier.fillMaxSize(),
                    showRows = true,
                    // Celý seznam řady: díly → Pořady (Nové), knihy → Knihovna.
                    onShowAllRow = { row ->
                        onSelectSection(
                            if (row == HomeRow.NEW_EPISODES || row == HomeRow.SHORT) SlovoSection.PORADY else SlovoSection.KNIHOVNA,
                        )
                    },
                )
            }
        }
        // Dětský sloučený poslech (schválené pořady a knihy) — beze změny, jen vlastní místo v liště.
        SlovoSection.POSLECH -> SlovoSectionScaffold("Poslech", onMenu) {
            ListenScreen(
                onOpenBook = onOpenBook,
                onEditBook = { id, title, author -> onPush(SlovoDetailEntry.AudiobookEdit(id, title, author)) },
                onOpenPodcast = { onPush(SlovoDetailEntry.PodcastDetail(it)) },
                onPlayEpisode = { itemId, episodeId ->
                    onPush(SlovoDetailEntry.AudiobookPlayer(itemId, fromStart = false, episodeId = episodeId))
                },
                onOpenSource = { src -> onPush(sourceDetail(src)) },
                onOpenMerged = { gid, gTitle -> onPush(SlovoDetailEntry.MergedPodcast(gid, gTitle)) },
                onOpenSourceEpisode = onOpenSourceEpisode,
                // SLOVO-KIDS-EPISODE / WATCHDOG — dítě otevře jen schválenou sérii, routing podle typu.
                onOpenSourceSeries = { src, slug, seriesTitle -> onPush(sourceSeriesDetail(src, slug, seriesTitle)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // AMPHORA F2 / KN1: Knihy | Pořady | Stažené (výchozí záložka v Nastavení › Knihovna).
        SlovoSection.KNIHOVNA -> {
            val ctx = LocalContext.current
            val pager = rememberPagerState(initialPage = LibraryPrefs.defaultTab(ctx)) { LibraryPrefs.tabs.size }
            val scope = rememberCoroutineScope()
            SlovoSectionScaffold(
                onMenu = onMenu,
                titleContent = { PagerTabs(pager, LibraryPrefs.tabs) { page -> scope.launch { pager.animateScrollToPage(page) } } },
            ) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                    when {
                        page == 2 -> DownloadsScreen(onOpenAudioPlayer = expandMiniPlayer, modifier = Modifier.fillMaxSize())
                        !listenState.isConfigured -> NotConfigured()
                        page == 0 -> BooksContent(
                            state = listenState,
                            viewModel = listenVm,
                            onOpenBook = onOpenBook,
                            onEditBook = { id, title, author -> onPush(SlovoDetailEntry.AudiobookEdit(id, title, author)) },
                        )
                        else -> FollowingContent(
                            state = listenState,
                            viewModel = listenVm,
                            onOpenPodcast = { onPush(SlovoDetailEntry.PodcastDetail(it)) },
                            onOpenSource = { src -> onPush(sourceDetail(src)) },
                            onOpenMerged = { gid, gTitle -> onPush(SlovoDetailEntry.MergedPodcast(gid, gTitle)) },
                            downloadCount = podcastDownloads.size,
                            sourceType = "all",
                        )
                    }
                }
            }
        }
        SlovoSection.PORADY -> {
            val pager = rememberPagerState(initialPage = 0) { 2 }
            val scope = rememberCoroutineScope()
            SlovoSectionScaffold(
                onMenu = onMenu,
                titleContent = {
                    PagerTabs(pager, listOf("Nové", "Sledované")) { page -> scope.launch { pager.animateScrollToPage(page) } }
                },
            ) {
                if (!listenState.isConfigured) NotConfigured()
                else HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    // F2: Nové díly první (Sledované jsou i v Knihovně › Pořady).
                    if (page == 1) {
                        FollowingContent(
                            state = listenState,
                            viewModel = listenVm,
                            onOpenPodcast = { onPush(SlovoDetailEntry.PodcastDetail(it)) },
                            onOpenSource = { src -> onPush(sourceDetail(src)) },
                            onOpenMerged = { gid, gTitle -> onPush(SlovoDetailEntry.MergedPodcast(gid, gTitle)) },
                            downloadCount = podcastDownloads.size,
                            sourceType = "all",
                        )
                    } else {
                        TimelinePage(
                            state = listenState,
                            viewModel = listenVm,
                            podcastDownloads = podcastDownloads,
                            onOpenSourceEpisode = onOpenSourceEpisode,
                            onGoToObjevit = { onSelectSection(SlovoSection.OBJEVIT) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        // AMPHORA F3 / HL1: jedno hledání (knihy, pořady, díly, Audioteka, nové pořady).
        SlovoSection.HLEDAT -> SlovoSectionScaffold(section.label, onMenu) {
            UnifiedSearchScreen(
                books = listenState.books,
                sources = listenState.customSources,
                onOpenBook = onOpenBook,
                onOpenSource = { src -> onPush(sourceDetail(src)) },
                onOpenNewShow = { r -> onPush(sourceDetail(PodcastSource(id = "", type = r.type, ref = r.ref, title = r.title))) },
                onOpenAudioteka = { onPush(audiotekaEntry(it)) },
                onPlayVideo = playExternalVideo,
                isKidsProfile = isKidsProfile,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SlovoSection.FRONTA -> SlovoSectionScaffold(section.label, onMenu) {
            QueueScreen(onOpenPlayer = expandMiniPlayer, modifier = Modifier.fillMaxSize())
        }
        SlovoSection.OBJEVIT -> SlovoSectionScaffold(section.label, onMenu) {
            PodcastDiscoveryScreen(modifier = Modifier.fillMaxSize(), showTitle = false)
        }
        SlovoSection.ZDROJE -> SlovoSectionScaffold(section.label, onMenu) {
            SourceManagerScreen(
                modifier = Modifier.fillMaxSize(),
                onUploadAudiobook = { onPush(SlovoDetailEntry.UploadAudiobook) },
                showTitle = false,
                onOpenSource = { src -> onPush(sourceDetail(src)) },
            )
        }
        SlovoSection.STAHOVANI -> SlovoSectionScaffold(if (isKidsProfile) "Stažené" else section.label, onMenu) {
            DownloadsScreen(onOpenAudioPlayer = expandMiniPlayer, modifier = Modifier.fillMaxSize())
        }
        SlovoSection.KATALOG -> SlovoSectionScaffold(section.label, onMenu) {
            AudiotekaCatalogScreen(
                onOpen = { onPush(audiotekaEntry(it)) },
                modifier = Modifier.fillMaxSize(),
                onOpenShelf = { onPush(SlovoDetailEntry.AudiotekaWeb(AUDIOTEKA_SHELF_URL)) }
                    .takeIf { AudiotekaPrefs.buyInApp(LocalContext.current) },
            )
        }
        SlovoSection.ZALOZKY -> SlovoSectionScaffold(section.label, onMenu) {
            val openers = remember(onPush, expandMiniPlayer) {
                BookmarkOpeners(
                    onOpenAt = { itemId, episodeId, sec -> onPush(SlovoDetailEntry.AudiobookPlayer(itemId, false, sec, episodeId)) },
                    onOpenPlayer = expandMiniPlayer,
                )
            }
            BookmarksScreen(openers = openers, modifier = Modifier.fillMaxSize())
        }
        SlovoSection.NASTAVENI -> SlovoSettingsScreen(onMenu = onMenu, isKidsProfile = isKidsProfile)
        SlovoSection.PROFIL -> SlovoProfileScreen(onMenu = onMenu)
    }
}

@Composable
private fun NotConfigured() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Poslech zatím není nastaven.\nPřihlas se k Audiobookshelf serveru v Nastavení → Účet / Audioknihy.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

/** Obal sekce: horní pruh (avatar + název) + obsah pod ním. */
@Composable
private fun SlovoSectionScaffold(
    title: String,
    onMenu: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SlovoSectionBar(title = title, onMenu = onMenu, trailing = trailing)
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/** Varianta s vlastním obsahem titulku místo prostého textu (viz [PagerTabs]). */
@Composable
private fun SlovoSectionScaffold(
    onMenu: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleContent: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SlovoSectionBar(onMenu = onMenu, trailing = trailing, content = titleContent)
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/**
 * Strany pageru viditelné v liště zároveň — aktivní svítí, neaktivní ztlumené, přechod sleduje prst
 * (user 2026-08-16 17:38). Ťuk na název = přeskok na stranu.
 */
@Composable
private fun PagerTabs(pagerState: PagerState, labels: List<String>, onSelect: (Int) -> Unit) {
    val maxIndex = (labels.size - 1).coerceAtLeast(0)
    val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, maxIndex.toFloat())
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        labels.forEachIndexed { index, label ->
            val distance = (position - index).absoluteValue.coerceIn(0f, 1f)
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (index == pagerState.currentPage) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = lerp(0.4f, 1f, 1f - distance)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onSelect(index) },
            )
        }
    }
}

/** Sdíleno mezi kartou zdroje ve Sledovaných a Timeline řádkem (viz [SlovoDetailHost.linkedOrPlain]). */
private fun sourceDetail(src: PodcastSource): SlovoDetailEntry = when (src.type) {
    "youtube" -> SlovoDetailEntry.YoutubeChannel(src.ref, src.title)
    "ctv" -> SlovoDetailEntry.CtvProgram(src.ref, src.title)
    else -> SlovoDetailEntry.RssPodcast(src.ref, src.title)
}

/** SLOVO-KIDS-EPISODE / WATCHDOG — dítě otevře jen schválenou sérii, routing podle typu zdroje. */
private fun sourceSeriesDetail(src: PodcastSource, slug: String, seriesTitle: String): SlovoDetailEntry =
    when (src.type) {
        "youtube" -> SlovoDetailEntry.YoutubeChannel(src.ref, seriesTitle, seriesFilter = slug)
        "ctv" -> SlovoDetailEntry.CtvProgram(src.ref, seriesTitle, seriesFilter = slug)
        else -> SlovoDetailEntry.RssPodcast(src.ref, seriesTitle, seriesFilter = slug)
    }
