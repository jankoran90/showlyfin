package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.feature.detail.ui.PersonFilmographySheet
import androidx.compose.runtime.LaunchedEffect

/** Role, pod kterými lze přidat osobu do Oblíbených (hvězda → menu). */
private val PERSON_ROLES = listOf(
    FavoriteKind.ACTOR to "Herec",
    FavoriteKind.DIRECTOR to "Režisér",
    FavoriteKind.WRITER to "Scénárista",
    FavoriteKind.PRODUCER to "Producent",
    FavoriteKind.COMPOSER to "Skladatel",
)

/**
 * COMPASS C3 (SHW-44) — univerzální hledání. Trvalé pole nahoře (z [AppTopBar]) otevře tuto obrazovku:
 * back + vstupní pole (autofocus) + přepínač rozsahu (Filmy/Seriály/Lidi/Vydavatelství) + sjednocená
 * mřížka výsledků. Hvězda u filmu/vydavatelství = prostý toggle, u osoby = volba role. Tap na osobu/
 * vydavatelství otevře jejich tvorbu; tap na film/seriál otevře detail.
 */
@Composable
internal fun SearchScreen(
    onOpenDetail: (tmdbId: Long, title: String, isShow: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    fun isFav(kind: FavoriteKind, id: Long) = favorites.any { it.kind == kind && it.id == id }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        // Hlavička: zpět + vstupní pole
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = MaterialTheme.colorScheme.onBackground)
            }
            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("Hledat filmy, lidi, studia…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Vymazat")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        // Rozsah hledání
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SearchScope.entries.toList()) { scope ->
                FilterChip(
                    selected = state.scope == scope,
                    onClick = { vm.onScopeChange(scope) },
                    label = { Text(scope.label) },
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.query.isBlank() -> CenteredHint("Začni psát a hledej napříč filmy, seriály, lidmi a vydavatelstvími.")
            state.results.isEmpty() -> CenteredHint("Nic nenalezeno. Zkus jiný výraz nebo přepni rozsah.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.results, key = { "${it::class.simpleName}_${it.id}" }) { result ->
                    when (result) {
                        is SearchResult.Movie -> PosterTile(
                            posterUrl = result.posterUrl, title = result.title, year = result.year,
                            favorite = isFav(FavoriteKind.MOVIE, result.id),
                            onStar = {
                                vm.toggleFavorite(FavoriteItem(FavoriteKind.MOVIE, result.id, result.title, result.posterUrl, result.year?.toIntOrNull()))
                            },
                            onClick = { onOpenDetail(result.id, result.title, false) },
                        )
                        is SearchResult.Show -> PosterTile(
                            posterUrl = result.posterUrl, title = result.title, year = result.year,
                            favorite = null, // seriály do Oblíbených nejdou (kategorie nemá SHOW)
                            onStar = {},
                            onClick = { onOpenDetail(result.id, result.title, true) },
                        )
                        is SearchResult.Person -> PersonTile(
                            person = result,
                            isFav = ::isFav,
                            onToggleRole = { kind ->
                                vm.toggleFavorite(FavoriteItem(kind, result.id, result.name, result.profileUrl))
                            },
                            onClick = { vm.openWorks(departmentToKind(result.department), result.id, result.name) },
                        )
                        is SearchResult.Company -> CompanyTile(
                            company = result,
                            favorite = isFav(FavoriteKind.COMPANY, result.id),
                            onStar = { vm.toggleFavorite(FavoriteItem(FavoriteKind.COMPANY, result.id, result.name, result.logoUrl)) },
                            onClick = { vm.openWorks(FavoriteKind.COMPANY, result.id, result.name) },
                        )
                    }
                }
            }
        }
    }

    if (sheet.open) {
        PersonFilmographySheet(
            name = sheet.name,
            loading = sheet.loading,
            collection = sheet.collection,
            roleLabel = sheet.roleLabel,
            onPartClick = { part ->
                part.tmdbId?.let { onOpenDetail(it, part.title, false) }
                vm.closeSheet()
            },
            onDismiss = { vm.closeSheet() },
        )
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PosterTile(
    posterUrl: String?,
    title: String,
    year: String?,
    favorite: Boolean?,
    onStar: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            ImageOrInitial(posterUrl, title, MaterialTheme.shapes.medium, ContentScale.Crop)
            if (favorite != null) StarBadge(favorite, onStar, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (!year.isNullOrBlank()) {
            Text(year, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun PersonTile(
    person: SearchResult.Person,
    isFav: (FavoriteKind, Long) -> Boolean,
    onToggleRole: (FavoriteKind) -> Unit,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val anyFav = PERSON_ROLES.any { (kind, _) -> isFav(kind, person.id) }
    Column(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (person.profileUrl != null) {
                AsyncImage(
                    model = person.profileUrl, contentDescription = person.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
            } else {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                    Text(person.name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StarBadge(anyFav, { menuOpen = true }, Modifier.align(Alignment.TopEnd))
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                PERSON_ROLES.forEach { (kind, label) ->
                    val checked = isFav(kind, person.id)
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { if (checked) Icon(Icons.Default.Check, contentDescription = null) },
                        onClick = { onToggleRole(kind) }, // menu zůstává otevřené → lze přidat víc rolí
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(person.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        val dep = departmentCz(person.department)
        if (dep.isNotBlank()) {
            Text(dep, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CompanyTile(
    company: SearchResult.Company,
    favorite: Boolean,
    onStar: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                Alignment.Center,
            ) {
                if (company.logoUrl != null) {
                    AsyncImage(model = company.logoUrl, contentDescription = company.name, contentScale = ContentScale.Fit)
                } else {
                    Text(company.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            StarBadge(favorite, onStar, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(company.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ImageOrInitial(url: String?, name: String, shape: androidx.compose.ui.graphics.Shape, scale: ContentScale) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = name, contentScale = scale, modifier = Modifier.fillMaxSize().clip(shape))
    } else {
        Box(Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StarBadge(filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), Alignment.Center) {
        IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
            Icon(
                if (filled) Icons.Default.Star else Icons.Outlined.StarBorder,
                contentDescription = if (filled) "V oblíbených" else "Přidat do oblíbených",
                tint = if (filled) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** known_for_department → výchozí kategorie Oblíbeného (pro tvorbu při tapu); null = veškerá tvorba. */
private fun departmentToKind(dep: String?): FavoriteKind? = when (dep?.lowercase()) {
    "acting" -> FavoriteKind.ACTOR
    "directing" -> FavoriteKind.DIRECTOR
    "writing" -> FavoriteKind.WRITER
    "production" -> FavoriteKind.PRODUCER
    "sound" -> FavoriteKind.COMPOSER
    else -> null
}

private fun departmentCz(dep: String?): String = when (dep?.lowercase()) {
    "acting" -> "Herec"
    "directing" -> "Režie"
    "production" -> "Produkce"
    "sound" -> "Hudba"
    "writing" -> "Scénář"
    "camera" -> "Kamera"
    "editing" -> "Střih"
    "art" -> "Výprava"
    "crew" -> "Štáb"
    else -> dep ?: ""
}
