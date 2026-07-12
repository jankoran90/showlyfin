package com.github.jankoran90.showlyfin.ui.tv.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.ui.tv.jellyfin.TvLibrariesContent

/**
 * TENFOOT (SHW-87) — sekce „Knihovna". Vrací procházení po Jellyfin knihovnách ztracené v redesignu 293:
 * mřížka knihoven ([TvLibrariesContent]) → klik → mřížka položek (drill `LibraryItems`). Jasné oddělení
 * „moje knihovna" vs. „Objevovat" (Trakt).
 */
@Composable
fun TvLibraryScreen(
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Text(
            text = "Knihovna",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        TvLibrariesContent(onOpenLibrary = onOpenLibrary)
    }
}
