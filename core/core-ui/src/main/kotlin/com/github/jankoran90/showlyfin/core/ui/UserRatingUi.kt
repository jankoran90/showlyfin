package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * BESPOKE (SHW-95) F3 — cíl hodnocení předaný z karty/detailu do sdíleného [RatingDialog] přes
 * [LocalUserRatingProvider]. [traktId] pro zrcadlení do Traktu (0 = jen lokálně).
 */
data class RatingTarget(
    val tmdbId: Long?,
    val imdbId: String?,
    val traktId: Long,
    val title: String,
    val year: Int?,
    val isShow: Boolean,
)

/**
 * Poskytovatel vlastního hodnocení pro karty a detail (vzor [LocalCsfdRatingProvider]). Wired JEDNOU v shellu
 * (RatingHost) → karty ho čtou přes [rememberCardRating] (odznak) a spouští [requestRate] (long-press / MENU
 * na TV / tlačítko v detailu). Bez providera (starý shell) = hodnocení se prostě nezobrazí ani nespustí.
 */
interface UserRatingProvider {
    /** Klíč [cardRatingKey] → hvězdy 1–10. Reaktivní (mění se po ohodnocení). */
    val ratings: StateFlow<Map<String, Int>>
    /** Otevři hvězdičkový dialog pro daný titul. */
    fun requestRate(target: RatingTarget)
}

val LocalUserRatingProvider = androidx.compose.runtime.staticCompositionLocalOf<UserRatingProvider?> { null }

/** Klíč hodnocení karty: tmdbId, fallback imdbId (shodný formát s data-uploader `ratingKey`). */
fun cardRatingKey(tmdbId: Long?, imdbId: String?): String? =
    tmdbId?.takeIf { it > 0L }?.let { "t$it" } ?: imdbId?.takeIf { it.isNotBlank() }?.let { "i$it" }

/** Aktuální hvězdy (1–10) titulu z [LocalUserRatingProvider], nebo null (nehodnoceno / bez providera). */
@Composable
fun rememberCardRating(tmdbId: Long?, imdbId: String?): Int? {
    val provider = LocalUserRatingProvider.current ?: return null
    val map by provider.ratings.collectAsStateWithLifecycle()
    val key = cardRatingKey(tmdbId, imdbId) ?: return null
    return map[key]
}

/** Malý odznak vlastního hodnocení (★N) na kartě — oranžová hvězda, tmavý scrim. */
@Composable
fun UserRatingBadge(stars: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xCC000000), androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = " $stars",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Sdílený hvězdičkový dialog 1–10 (telefon i TV). Řada 10 hvězd; klik/D-pad na hvězdu N zvolí N, „Uložit"
 * potvrdí, „Odebrat" zruší hodnocení (jen pokud už existuje). Funguje D-padem na TV (hvězdy jsou IconButtony).
 */
@Composable
fun RatingDialog(
    target: RatingTarget,
    current: Int?,
    onRate: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(target) { mutableIntStateOf(current ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ohodnotit: ${target.title}") },
        text = {
            androidx.compose.foundation.layout.Column {
                listOf(1..5, 6..10).forEach { range ->
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        range.forEach { i ->
                            IconButton(onClick = { selected = i }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (i <= selected) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "$i",
                                    tint = if (i <= selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (selected in 1..10) onRate(selected) }, enabled = selected in 1..10) {
                Text(if (selected > 0) "Uložit $selected/10" else "Uložit")
            }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = onClear) { Text("Odebrat") }
            } else {
                TextButton(onClick = onDismiss) { Text("Zrušit") }
            }
        },
    )
}
