package com.github.jankoran90.showlyfin.feature.detail.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw

private val CsfdRed = Color(0xFFBA0305)

// ── AMOLED dark amber paleta pro sheet recenzí (vysoký kontrast na černé) ──
private val AmoledBg = Color(0xFF000000)            // pravá černá (AMOLED)
private val AmoledCard = Color(0xFF161310)          // tmavá karta s nádechem amber
private val Amber = Color(0xFFFFB74D)               // amber akcent (nadpis, hvězdy, jméno)
private val AmberText = Color(0xFFF2E8D5)           // teplý světlý text (popis recenze)
private val AmberDim = Color(0xFFB9A88C)            // ztlumený (datum)
private val StarEmpty = Color(0xFF5A5346)           // prázdná hvězda (čitelná na černé)

@Composable
fun CsfdRatingBadge(rating: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    Box(
        modifier = modifier
            .background(CsfdRed, RoundedCornerShape(6.dp))
            .padding(horizontal = if (big) 10.dp else 8.dp, vertical = if (big) 5.dp else 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ČSFD",
                color = Color.White,
                style = if (big) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.padding(horizontal = if (big) 5.dp else 4.dp))
            Text(
                text = "$rating%",
                color = Color.White,
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Hvězdičky 0–5 z ČSFD ratingu uživatele (0–100 %, stars = rating/20). */
@Composable
private fun CsfdStars(
    rating: Int?,
    modifier: Modifier = Modifier,
    filled: Color = CsfdRed,
    empty: Color = StarEmpty,
) {
    if (rating == null) return
    val stars = (rating / 20).coerceIn(0, 5)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            Icon(
                imageVector = if (i < stars) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (i < stars) filled else empty,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Karta recenze v AMOLED dark amber vzhledu (vysoký kontrast). */
@Composable
private fun CsfdReviewCardAmoled(review: CsfdReviewRaw, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AmoledCard, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = review.username,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Amber,
            )
            CsfdStars(rating = review.rating, filled = Amber)
        }
        if (review.date.isNotBlank()) {
            Text(text = review.date, style = MaterialTheme.typography.labelSmall, color = AmberDim)
        }
        Spacer(Modifier.height(6.dp))
        Text(text = review.text, style = MaterialTheme.typography.bodySmall, color = AmberText)
    }
}

/** Fullscreen ČSFD galerie fotek — swipovatelný HorizontalPager (F3). */
@Composable
fun CsfdGalleryDialog(
    urls: List<String>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier.fillMaxSize().background(AmoledBg),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Amber)
                urls.isEmpty() -> Text("Galerie není k dispozici", color = AmberText)
                else -> {
                    val pagerState = rememberPagerState(pageCount = { urls.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = urls[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${urls.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
            }
        }
    }
}

/** Bottom sheet se všemi (~20) ČSFD recenzemi — AMOLED dark amber. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsfdReviewsBottomSheet(
    reviews: List<CsfdReviewRaw>,
    title: String,
    year: Int?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val heading = buildString {
        append("ČSFD recenze")
        if (title.isNotBlank()) append(" · $title")
        year?.let { append(" ($it)") }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBg,
        modifier = modifier,
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Amber,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(reviews) { review -> CsfdReviewCardAmoled(review = review) }
        }
    }
}
