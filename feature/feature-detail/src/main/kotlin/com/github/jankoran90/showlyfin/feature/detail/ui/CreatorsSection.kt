package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.CollectionSection
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
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
) {
    if (cast.isEmpty() && directors.isEmpty() && writers.isEmpty() && cinematographers.isEmpty()) return

    Spacer(Modifier.height(12.dp))
    Text(
        text = "Tvůrci",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    if (cast.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cast) { person -> ActorChip(person = person, onClick = { onPersonClick(person, FavoriteKind.ACTOR) }) }
        }
    }

    // Jemné řádky pod pásem — Režie / Scénář / Kamera.
    val rows = listOf<Triple<String, List<TmdbPerson>, FavoriteKind?>>(
        Triple("Režie", directors, FavoriteKind.DIRECTOR),
        Triple("Scénář", writers, null),
        Triple("Kamera", cinematographers, null),
    ).filter { it.second.isNotEmpty() }
    if (rows.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            rows.forEach { (label, people, kind) -> CrewRow(label = label, people = people, kind = kind, onPersonClick = onPersonClick) }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ActorChip(person: TmdbPerson, onClick: () -> Unit) {
    val character = person.character ?: person.roles?.firstOrNull()?.character
    Column(
        modifier = Modifier
            .width(84.dp)
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

/**
 * Plan ENSEMBLE: spodní list s tvorbou (filmografií) zvolené osoby. Karty nesou tmdbId,
 * takže klik otevře platný detail přes [onPartClick] (stejná navigace jako kolekce).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonFilmographySheet(
    name: String?,
    loading: Boolean,
    collection: MediaCollection?,
    onPartClick: (CollectionPart) -> Unit,
    onDismiss: () -> Unit,
    canFavorite: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name?.takeIf { it.isNotBlank() }?.let { "Tvorba — $it" } ?: "Tvorba",
                    style = MaterialTheme.typography.titleLarge,
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
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                collection == null || collection.parts.isEmpty() -> Text(
                    "Pro tuto osobu nemáme žádné filmy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp),
                )
                else -> CollectionSection(
                    collection = collection,
                    excludeKey = null,
                    onPartClick = onPartClick,
                )
            }
        }
    }
}
