package com.github.jankoran90.showlyfin.feature.discover.podcasts

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/**
 * OPONA (user 2026-10-13: „pak by mohlo být ve Filmotéce v Další díly") — rozkoukané pořady (YouTube, video
 * podcasty, i videa z hledání a z jiného zařízení) přidané do řady „Další díly" Filmotéky na TV i v telefonu.
 * Vypínač Nastavení → Pořady ([PodcastPrefs.IN_FILMOTEKA_NEXTUP]). Data = sdílený [PodcastsViewModel].
 */
class PoradyNextUp(val items: List<HomeRowItem>, private val vm: PodcastsViewModel?) {
    fun handles(item: HomeRowItem): Boolean = vm != null && PodcastItemKey.parse(item.key) is PodcastItemKey.Episode

    fun play(item: HomeRowItem, onPlay: (PodcastsViewModel.PlayTarget) -> Unit) {
        val ref = PodcastItemKey.parse(item.key) as? PodcastItemKey.Episode ?: return
        val v = vm ?: return
        val src = v.sourceOf(ref.sourceKey) ?: return
        val ep = v.episodeOf(ref.sourceKey, ref.episodeId) ?: return
        v.playTarget(src, ep)?.let(onPlay)
    }
}

/** [masterEnabled] = hlavní vypínač Filmotéka → „Další díly nahoře" (vypnutý skryje celou řadu, i pořady). */
@Composable
fun rememberPoradyNextUp(masterEnabled: Boolean): PoradyNextUp {
    val prefs = LocalContext.current.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE)
    val enabled = remember { PodcastPrefs.inFilmotekaNextUp(prefs) }
    if (!enabled || !masterEnabled) return PoradyNextUp(emptyList(), null)
    val vm: PodcastsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onEnter() }
    val items = remember(state, marks, translated, translating, watched) {
        val rows = PodcastPrefs.Rows(continueRow = true, newRow = false, channelsRow = false, newLimit = 0)
        podcastShelves(state, marks, translated, translating, rows, watched).continueItems.take(MAX_IN_NEXTUP)
    }
    return PoradyNextUp(items, vm)
}

private const val MAX_IN_NEXTUP = 10
