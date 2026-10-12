package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.abs.model.AudiobookDetail
import com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse

/**
 * Výsledek nahrání audioknihy (2026-10-09, user: „nikde nevidím hotovo, výsledek a metadata pro
 * kontrolu, obrázek atd."). Ukáže, co ABS o knize opravdu má — obálku, autora, vypravěče, rok,
 * vydavatele, délku, kapitoly, popis — a nabídne rovnou úpravu. [detail] = živý stav z ABS
 * (null = ještě se načítá), [enrichPending] = server ještě dohledává metadata na pozadí.
 */
@Composable
internal fun UploadResultCard(
    res: AudiobookUploadResponse,
    detail: AudiobookDetail?,
    coverUrl: String?,
    enrichPending: Boolean,
    onEdit: (itemId: String, title: String, author: String?) -> Unit,
    onBack: () -> Unit,
    onAgain: () -> Unit,
) {
    val onCard = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = onCard)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Kniha nahrána",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onCard,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (res.itemId == null) {
                // Server knihu po scanu v knihovně nenašel (nebo ještě ne) — žádná data ke kontrole.
                Text(
                    "Soubory jsou na serveru, ale knihovna knihu zatím nenačetla. Zkontroluj ji za chvíli v seznamu knih.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard,
                )
            } else {
                Row {
                    if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Obálka",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(112.dp).clip(MaterialTheme.shapes.small),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        val book = detail?.book
                        Text(
                            book?.title ?: res.title ?: "—",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onCard,
                        )
                        MetaLine("Autor", book?.author ?: res.author)
                        MetaLine("Vypravěč", book?.narrator)
                        MetaLine("Série", book?.seriesName?.let { s -> book.seriesSequence?.let { "$s #$it" } ?: s })
                        MetaLine("Rok", detail?.publishedYear)
                        MetaLine("Žánr", detail?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", "))
                        MetaLine("Délka", book?.durationSec?.takeIf { it > 0 }?.let(::formatBookLength))
                        if (detail != null) {
                            MetaLine("Kapitoly", if (detail.chapters.isEmpty()) "žádné" else "${detail.chapters.size}")
                        }
                        MetaLine("Souborů", res.tracks.takeIf { it > 0 }?.toString())
                    }
                }
                detail?.description?.let { raw ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        cleanAudiobookDescription(raw),
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (enrichPending || detail == null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = onCard)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (detail == null) "Načítám knihu z knihovny…" else "Dohledávám obálku a popis…",
                            style = MaterialTheme.typography.bodySmall,
                            color = onCard,
                        )
                    }
                } else if (detail.description == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Popis se nenašel — doplň ho přes Zkontrolovat / Upravit → Dohledat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            res.itemId?.let { id ->
                Button(
                    onClick = { onEdit(id, detail?.book?.title ?: res.title.orEmpty(), detail?.book?.author ?: res.author) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Zkontrolovat / Upravit")
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAgain) { Text("Nahrát další") }
                OutlinedButton(onClick = onBack) { Text("Zpět") }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 20700 s → „5 h 45 min". */
private fun formatBookLength(sec: Double): String {
    val total = (sec / 60).toLong()
    val h = total / 60
    val m = total % 60
    return if (h > 0) "$h h $m min" else "$m min"
}
