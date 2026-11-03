package com.github.jankoran90.showlyfin.feature.listen.audioteka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.CoverCardShape
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBook
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBookDetail
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenExpressiveTheme

/** Detail knihy z katalogu Audioteky: info, cena, Koupit (stránka Audioteky), návrhy pod tím. */
@Composable
fun AudiotekaBookScreen(
    bookId: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (AtBook) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiotekaBookViewModel = hiltViewModel(key = "at-book-$bookId"),
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val st by viewModel.state.collectAsStateWithLifecycle()
    val hideOwned = AudiotekaPrefs.hideOwned(LocalContext.current)
    AtScaffold(title, onBack, modifier) { pad ->
        when (val s = st) {
            is AtLoad.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                s.data.detail?.let { d -> item { BookHeader(d, onOpen) } }
                // Série obsahuje i tuhle knihu → ťuk na ni nepřidá stejný detail podruhé.
                val openOther: (AtBook) -> Unit = { if (it.id != bookId || it.inLibrary != null) onOpen(it) }
                items(s.data.sections, key = { it.kind + it.id }) { AtShelf(it, openOther, hideOwned = hideOwned, currentId = bookId) }
            }
            AtLoad.Failed -> Retry(pad) { viewModel.load(bookId, force = true) }
            else -> Loading(pad)
        }
    }
}

@Composable
private fun BookHeader(d: AtBookDetail, onOpen: (AtBook) -> Unit) {
    val b = d.book
    val uri = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(
                model = b.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(140.dp).clip(CoverCardShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (b.authors.isNotEmpty()) Text(b.authors.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                if (b.lectors.isNotEmpty()) Meta("Čte: " + b.lectors.joinToString(", "))
                b.minutes?.let { Meta(formatMinutes(it)) }
                b.publisher?.let { Meta(it) }
                b.rating?.let { Meta("Hodnocení %.1f / 5".format(it)) }
            }
        }
        when {
            b.inLibrary != null -> Button(onClick = { onOpen(b) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LibraryBooks, contentDescription = null)
                Text("  Máš v knihovně — otevřít")
            }
            b.url != null -> {
                val price = b.price ?: if (b.free) "zdarma" else null
                Button(onClick = { uri.openUri(b.url!!) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ShoppingCart, contentDescription = null)
                    Text("  Koupit na Audiotéce" + (price?.let { " · $it" } ?: ""))
                }
                val club = listOfNotNull(
                    b.priceClub?.let { "v Klubu $it" },
                    "v předplatném Klubu".takeIf { b.inClub },
                    b.priceRegular?.takeIf { it != b.price }?.let { "běžně $it" },
                )
                if (club.isNotEmpty()) Meta(club.joinToString(" · "))
            }
        }
        if (b.inLibrary == null && b.url != null) {
            FilledTonalButton(onClick = { uri.openUri(b.url!!) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                Text("  Ukázka a recenze na Audiotéce")
            }
        }
        if (d.categories.isNotEmpty()) Meta(d.categories.joinToString(" · "))
        d.description?.let { desc ->
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (desc.length > 300) TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Méně" else "Celý popis")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Série / kolekce z Audioteky: všechny díly v pořadí, co máš je označené. */
@Composable
fun AudiotekaSeriesScreen(
    seriesId: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (AtBook) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiotekaSeriesViewModel = hiltViewModel(key = "at-series-$seriesId"),
) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val st by viewModel.state.collectAsStateWithLifecycle()
    AtScaffold(title, onBack, modifier) { pad ->
        when (val s = st) {
            is AtLoad.Ready -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize().padding(pad),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val owned = s.data.items.count { it.inLibrary != null }
                    Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${s.data.items.size} dílů · máš $owned", style = MaterialTheme.typography.titleSmall)
                        s.data.description?.let { Meta(it) }
                    }
                }
                // Série ze hledání: díly číslované, bez opakovaného jména série (kolekce beze změny).
                val numbered = numberedSeries(s.data.title, s.data.items)
                itemsIndexed(s.data.items, key = { _, b -> b.id }) { i, b ->
                    if (numbered) AtCoverCard(b, onOpen, title = b.seriesTitle(s.data.title), subtitle = b.seriesSubtitle(i + 1))
                    else AtCoverCard(b, onOpen)
                }
            }
            AtLoad.Empty -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Série je prázdná.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AtLoad.Failed -> Retry(pad) { viewModel.load(seriesId, force = true) }
            else -> Loading(pad)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    ListenExpressiveTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            content = content,
        )
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Loading(pad: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Retry(pad: PaddingValues, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nepodařilo se načíst z Audioteky.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text("Zkusit znovu") }
    }
}

private fun formatMinutes(m: Int): String = if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
