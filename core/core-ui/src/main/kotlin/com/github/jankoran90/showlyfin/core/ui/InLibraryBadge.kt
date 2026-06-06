package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InLibraryBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "V knihovně",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun InLibraryTitleBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(2.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "V knihovně",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(10.dp),
        )
    }
}

@Composable
fun InLibraryTitleBadgeSpacer() {
    Spacer(Modifier.width(4.dp))
}
