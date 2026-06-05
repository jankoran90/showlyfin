package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw

private val CsfdRed = Color(0xFFBA0305)

@Composable
fun CsfdRatingBadge(rating: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CsfdRed, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ČSFD",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                text = "$rating%",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
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
                review.rating?.let {
                    Text(
                        text = "$it%",
                        style = MaterialTheme.typography.labelMedium,
                        color = CsfdRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
                maxLines = 6,
            )
        }
    }
}

@Composable
fun CsfdReviewsSection(reviews: List<CsfdReviewRaw>, modifier: Modifier = Modifier) {
    if (reviews.isEmpty()) return
    Column(modifier = modifier) {
        Text(
            text = "ČSFD recenze",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reviews.forEach { review ->
                CsfdReviewCard(review = review)
            }
        }
    }
}
