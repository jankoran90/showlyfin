package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.CollectionGrid
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.CollectionSection
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind

/**
 * Plan ENSEMBLE (SHW-45) — sekce „Tvůrci" na detailu (nad kolekcemi).
 *
 * Pás herců (kruhové portréty + jméno + role), pod ním jemně řádky **Režie / Scénář / Kamera**
 * (místo hudby). Klik na kohokoli → [onPersonClick] → spodní list s jeho tvorbou (validní karty).
 */
@Composable
fun CreatorsSection(
    cast: List<TmdbPerson>,
    directors: List<TmdbPerson>,
    writers: List<TmdbPerson>,
    cinematographers: List<TmdbPerson>,
    onPersonClick: (TmdbPerson, FavoriteKind?) -> Unit,
    // VANTAGE D4: žánry jako druhý sloupec vedle Scénář/Kamera (stejný design jako crew řádky).
    // [onGenreClick] zatím null = neproklikávací; později žánr × režisér (handoff VANTAGE).
    genres: List<String> = emptyList(),
    onGenreClick: ((String) -> Unit)? = null,
    // VANTAGE (SHW-48): Scénář/Kamera + žánry se odhalí jen při rozbaleném popisu (default skryté);
    // pás herců + režisérů je VŽDY vidět.
    detailsVisible: Boolean = true,
) {
    if (cast.isEmpty() && directors.isEmpty() && writers.isEmpty() &&
        cinematographers.isEmpty() && genres.isEmpty()) return

    Spacer(Modifier.height(12.dp))
    Text(
        text = "Tvůrci",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    // Pás portrétů — režiséři jako PRVNÍ (role „Režie", proklik = režijní tvorba), pak herci. VŽDY vidět.
    if (cast.isNotEmpty() || directors.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(directors) { person ->
                ActorChip(person = person, roleOverride = "Režie", onClick = { onPersonClick(person, FavoriteKind.DIRECTOR) })
            }
            items(cast) { person ->
                ActorChip(person = person, onClick = { onPersonClick(person, FavoriteKind.ACTOR) })
            }
        }
    }

    // Pod rozbaleným popisem: jemné řádky Scénář / Kamera vlevo + Žánry vpravo (režie je v pásu výše).
    if (detailsVisible) {
        val rows = listOf<Triple<String, List<TmdbPerson>, FavoriteKind?>>(
            Triple("Scénář", writers, FavoriteKind.WRITER),
            Triple("Kamera", cinematographers, null), // kameramani zatím bez kategorie Oblíbených
        ).filter { it.second.isNotEmpty() }
        val hasCrew = rows.isNotEmpty()
        val hasGenres = genres.isNotEmpty()
        if (hasCrew || hasGenres) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasCrew) {
                    Column(Modifier.weight(1f)) {
                        rows.forEach { (label, people, kind) -> CrewRow(label = label, people = people, kind = kind, onPersonClick = onPersonClick) }
                    }
                }
                if (hasGenres) {
                    Column(Modifier.weight(1f)) { GenreRow(genres = genres, onGenreClick = onGenreClick) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** VANTAGE D4: žánry ve stejném designu jako [CrewRow] — label „Žánry" + seznam žánrů pod sebou.
 *  [onGenreClick] zatím null (neproklikávací) → barva jako neutrální text; až bude klik = primary. */
@Composable
private fun GenreRow(genres: List<String>, onGenreClick: ((String) -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            "Žánry ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(64.dp),
        )
        Column(Modifier.weight(1f)) {
            genres.forEach { g ->
                Text(
                    g.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (onGenreClick != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = (if (onGenreClick != null) Modifier.clickable { onGenreClick(g) } else Modifier)
                        .padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun ActorChip(person: TmdbPerson, onClick: () -> Unit, roleOverride: String? = null) {
    val character = roleOverride ?: person.character ?: person.roles?.firstOrNull()?.character
    Column(
        modifier = Modifier
            .width(84.dp)
            .tvFocusable(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val img = person.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    person.name?.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            person.name.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!character.isNullOrBlank()) {
            Text(
                character,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CrewRow(label: String, people: List<TmdbPerson>, kind: FavoriteKind?, onPersonClick: (TmdbPerson, FavoriteKind?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(64.dp),
        )
        Column(Modifier.weight(1f)) {
            people.forEach { p ->
                Text(
                    p.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onPersonClick(p, kind) }
                        .padding(vertical = 1.dp),
                )
            }
        }
    }
}

/** CANVAS (SHW-47) D: řazení filmografie. */
enum class FilmographySort { YEAR, RATING, POPULARITY }

/**
 * Plan ENSEMBLE + CANVAS (SHW-47) C/D: tvorba (filmografie) zvolené osoby jako **celoobrazovkový,
 * scrollovatelný** seznam (mřížka karet ve stylu Objevit, ne vytahovací okno) s řazením
 * (rok ↓ default / hodnocení / oblíbenost, směr přepínatelný). Karty nesou tmdbId → klik otevře
 * platný detail přes [onPartClick]. Plnoobrazovkový `Dialog` (feature-detail nemá activity-compose
 * → Back řeší Dialog nativně).
 */
@Composable
fun PersonFilmographySheet(
    name: String?,
    loading: Boolean,
    collection: MediaCollection?,
    // VANTAGE (SHW-48): rolový titulek listu (Herecká tvorba / Režie / Hudba …); null = „Tvorba".
    roleLabel: String? = null,
    onPartClick: (CollectionPart) -> Unit,
    onDismiss: () -> Unit,
    canFavorite: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
) {
    var sort by remember { mutableStateOf(FilmographySort.YEAR) }
    var descending by remember { mutableStateOf(true) }
    val parts = remember(collection, sort, descending) {
        val list = collection?.parts.orEmpty()
        val asc = when (sort) {
            FilmographySort.YEAR -> list.sortedBy { it.year?.take(4)?.toIntOrNull() ?: 0 }
            FilmographySort.RATING -> list.sortedBy { it.rating ?: 0f }
            FilmographySort.POPULARITY -> list.sortedBy { it.popularity ?: 0f }
        }
        if (descending) asc.asReversed() else asc
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                    Text(
                        text = (roleLabel?.takeIf { it.isNotBlank() } ?: "Tvorba").let { prefix ->
                            name?.takeIf { it.isNotBlank() }?.let { "$prefix — $it" } ?: prefix
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canFavorite) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Oblíbené",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortChip("Rok", FilmographySort.YEAR, sort, descending) { s, d -> sort = s; descending = d }
                    SortChip("Hodnocení", FilmographySort.RATING, sort, descending) { s, d -> sort = s; descending = d }
                    SortChip("Oblíbenost", FilmographySort.POPULARITY, sort, descending) { s, d -> sort = s; descending = d }
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> CollectionGrid(
                        parts = parts,
                        onPartClick = onPartClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Chip řazení — klik na nevybraný = nastaví (default sestupně); klik na vybraný = přepne směr ↓/↑. */
@Composable
private fun SortChip(
    label: String,
    value: FilmographySort,
    current: FilmographySort,
    descending: Boolean,
    onSelect: (FilmographySort, Boolean) -> Unit,
) {
    val selected = current == value
    FilterChip(
        selected = selected,
        onClick = { if (selected) onSelect(value, !descending) else onSelect(value, true) },
        label = { Text(label) },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = if (descending) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (descending) "Sestupně" else "Vzestupně",
                )
            }
        } else null,
    )
}
