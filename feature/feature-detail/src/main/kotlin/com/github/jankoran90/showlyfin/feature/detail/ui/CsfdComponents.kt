package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw

private val CsfdRed = Color(0xFFBA0305)

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
private fun CsfdStars(rating: Int?, modifier: Modifier = Modifier) {
    if (rating == null) return
    val stars = (rating / 20).coerceIn(0, 5)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            Icon(
                imageVector = if (i < stars) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (i < stars) CsfdRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun CsfdReviewCard(review: CsfdReviewRaw, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = review.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                CsfdStars(rating = review.rating)
            }
            if (review.date.isNotBlank()) {
                Text(
                    text = review.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = review.text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Bottom sheet se všemi (~20) ČSFD recenzemi + hvězdičkami uživatele. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsfdReviewsBottomSheet(
    reviews: List<CsfdReviewRaw>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Text(
            text = "ČSFD recenze (${reviews.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(reviews) { review -> CsfdReviewCard(review = review) }
        }
    }
}
