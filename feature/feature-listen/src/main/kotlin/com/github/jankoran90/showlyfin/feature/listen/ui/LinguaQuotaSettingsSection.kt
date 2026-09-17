package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.LinguaQuotaViewModel
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs

/**
 * LINGUA-YT VLNY (Slovo, user 2026-09-18): kategorický blok „AI překlad YouTube titulků" v
 * Nastavení → Poslech. Limit 5h mozek kvóty, od kterého appka pozastaví auto-pokračování vln a
 * vyžádá si potvrzení i přes riziko vyčerpání. Self-contained (vlastní VM). Parita Nastavení.
 */
@Composable
fun LinguaQuotaSettingsSection(
    viewModel: LinguaQuotaViewModel = hiltViewModel(),
) {
    val quotaLimit by viewModel.quotaLimit.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Limit mozek kvóty (5h okno)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "AI překlad YouTube titulků jede po vlnách sám dál, dokud je spotřeba pod tímto limitem. " +
                "Nad limitem appka pozastaví a zeptá se, jestli pokračovat i přes riziko vyčerpání.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerPrefs.LINGUA_QUOTA_LIMIT_OPTIONS.forEach { pct ->
                FilterChip(
                    selected = quotaLimit == pct,
                    onClick = { viewModel.setQuotaLimit(pct) },
                    label = { Text("$pct %") },
                )
            }
        }
    }
}
