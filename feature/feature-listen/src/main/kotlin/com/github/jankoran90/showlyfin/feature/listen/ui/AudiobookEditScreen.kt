package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.listen.AudiobookEditViewModel

/**
 * DROPSHIP F2c — úprava metadata + cover u stávající audioknihy.
 * Název/autor editovatelné, „Dohledat" = Audiolibrix (cz_book_lookup + PATCH + cover),
 * „Obálka" = vlastní cover ze SAF, „Uložit" = PATCH title/author do ABS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookEditScreen(
    itemId: String,
    initialTitle: String,
    initialAuthor: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiobookEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { viewModel.init(itemId, initialTitle, initialAuthor) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.uploadCover(uri) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Upravit knihu", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Název a autora můžeš upravit ručně, nebo nechat dohledat z Audiolibrix " +
                        "(české knihy — doplní i obálku a popis).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Název knihy") },
                )
            }
            item {
                OutlinedTextField(
                    value = state.author,
                    onValueChange = viewModel::onAuthorChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Autor (více autorů odděl čárkou)") },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = viewModel::doMatch,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isWorking,
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Text("  Dohledat")
                    }
                    Button(
                        onClick = { coverLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isWorking,
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Text("  Obálka")
                    }
                }
            }
            item {
                // EXCISE (2026-08-19, user "dej moznost vyhledat obrázek coveru online") — narozdíl
                // od "Dohledat" (auto-apply nejlepší CZ shody) tohle jen NABÍDNE grid kandidátů,
                // user si vybere sám (užitečné, když auto-match trefí špatnou edici/obálku).
                androidx.compose.material3.OutlinedButton(
                    onClick = viewModel::searchCovers,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isWorking,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Text("  Vyhledat obálku online")
                }
            }
            item {
                Button(
                    onClick = { viewModel.save(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isWorking,
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Text("  Uložit")
                }
            }
            if (state.isWorking) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (state.coverSearchOpen) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissCoverSearch) {
            Text(
                "Vyber obálku",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(state.coverCandidates) { c ->
                    androidx.compose.foundation.layout.Column(
                        Modifier.clickable { viewModel.applyCover(c.cover) },
                    ) {
                        AsyncImage(
                            model = c.cover,
                            contentDescription = c.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            c.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
