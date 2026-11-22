package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.highlightMatches

/**
 * Slovo — Nastavení. AMPHORA F6 (NA1, kánon ui-canon „Nastavení"): místo jednoho dlouhého seznamu
 * sbalovacích karet ROZCESTNÍK kategorií ([SlovoSettingsCategory]) → podstránka se sekcemi z
 * [slovoSettingEntries] + hledání napříč všemi volbami (titulek + kategorie + slova z popisků voleb).
 * Navigace je uvnitř obrazovky (stav + [BackHandler]), navController se nedotýká.
 * Ťuk na výsledek hledání otevře kategorii s tou sekcí rozbalenou a dorolovanou.
 */
@Composable
fun SlovoSettingsScreen(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Audit dětí (2026-10-12, user „Nastavení jen co se netýká obsahu"): dětský profil vidí jen
     * Vzhled, Zobrazení Domů, Knihovnu (záložka, řazení, mřížka) a O aplikaci (sekce s `adultOnly = false`).
     */
    isKidsProfile: Boolean = false,
) {
    val entries = remember(isKidsProfile) {
        slovoSettingEntries.filter { !isKidsProfile || !it.adultOnly }
    }
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var focusTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val category = openCategory?.let { name -> SlovoSettingsCategory.entries.firstOrNull { it.name == name } }

    BackHandler(enabled = category != null) {
        openCategory = null
        focusTitle = null
    }

    Column(modifier.fillMaxSize()) {
        if (category == null) {
            SlovoSectionBar(title = "Nastavení", onMenu = onMenu)
            SettingsHome(
                entries = entries,
                isKidsProfile = isKidsProfile,
                query = query,
                onQuery = { query = it },
                onOpen = { cat, focus ->
                    openCategory = cat.name
                    focusTitle = focus
                },
            )
        } else {
            SlovoSectionBar(onMenu = onMenu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { openCategory = null; focusTitle = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět na Nastavení")
                    }
                    Text(
                        category.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SettingsCategoryPage(
                sections = entries.filter { it.category == category },
                focusTitle = focusTitle,
            )
        }
    }
}

@Composable
private fun SettingsHome(
    entries: List<SlovoSettingEntry>,
    isKidsProfile: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (SlovoSettingsCategory, String?) -> Unit,
) {
    val hlBg = MaterialTheme.colorScheme.primary
    val hlFg = MaterialTheme.colorScheme.onPrimary
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Hledat v nastavení") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Vymazat") } }
        } else null,
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isKidsProfile) {
            Text(
                "Ostatní nastavení jsou dostupná jen v profilu dospělého.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (query.isBlank()) {
            SlovoSettingsCategory.entries.forEach { cat ->
                val inCat = entries.filter { it.category == cat }
                if (inCat.isEmpty()) return@forEach
                SettingsTile(
                    icon = cat.icon,
                    title = AnnotatedString(cat.title),
                    subtitle = AnnotatedString(
                        if (isKidsProfile) inCat.joinToString(" · ") { it.title } else cat.subtitle,
                    ),
                    onClick = { onOpen(cat, null) },
                )
            }
        } else {
            val hits = entries.filter { it.matches(query) }
            if (hits.isEmpty()) {
                Text(
                    "Nic nenalezeno. Zkus jiné slovo, třeba „rychlost“, „spánek“ nebo „stažené“.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
            hits.forEach { entry ->
                SettingsTile(
                    icon = entry.icon,
                    title = highlightMatches(entry.title, query, hlBg, hlFg),
                    subtitle = AnnotatedString(entry.category.title),
                    onClick = { onOpen(entry.category, entry.title) },
                )
            }
        }
    }
}

@Composable
private fun SettingsTile(
    icon: ImageVector,
    title: AnnotatedString,
    subtitle: AnnotatedString,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Podstránka kategorie: její sbalovací sekce. Jediná sekce nebo sekce z hledání je rovnou rozbalená. */
@Composable
private fun SettingsCategoryPage(
    sections: List<SlovoSettingEntry>,
    focusTitle: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sections.forEach { entry ->
            val focused = entry.title == focusTitle
            val requester = remember { BringIntoViewRequester() }
            if (focused) {
                LaunchedEffect(entry.title) { requester.bringIntoView() }
            }
            SlovoCollapsibleSection(
                title = entry.title,
                icon = entry.icon,
                initiallyExpanded = focused || sections.size == 1,
                modifier = Modifier.bringIntoViewRequester(requester),
                content = entry.content,
            )
        }
    }
}
