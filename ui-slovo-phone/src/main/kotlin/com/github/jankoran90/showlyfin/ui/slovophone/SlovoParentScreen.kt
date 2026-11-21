package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.KidsDisplayPrefs
import com.github.jankoran90.showlyfin.core.domain.PinHasher
import com.github.jankoran90.showlyfin.feature.listen.DownloadsViewModel
import com.github.jankoran90.showlyfin.feature.listen.ui.DownloadFormat

/**
 * AMPHORA F7 (DE1, katalog fúze: „jedno místo Pro rodiče ukončí hledání") — rodičovská obrazovka
 * v dětském profilu. Vchod: podržení levého horního rohu 3 s (dřív rovnou nabídka Víc) → PIN
 * dospělého → tahle obrazovka. Bez PINu v appce se otevře rovnou (s výzvou PIN nastavit).
 * Obsah: schválené (souhrn, spravuje se v Rodičovské appce), v telefonu, zobrazení poličky
 * ([KidsDisplayPrefs] v configu Dětí → server), dětský telefon (kiosek), přepnout profil, Nastavení.
 * Limit a večerka záměrně NE (v katalogu jen návrh, user nevybral).
 */
@Composable
internal fun SlovoParentHost(
    booksCount: Int,
    onClose: () -> Unit,
    onSelect: (SlovoSection) -> Unit,
    vm: SlovoParentViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var unlocked by remember { mutableStateOf(false) }
    val pin = ui.guardPinHash
    if (!ui.loaded) {
        // Revize F7 P1: než je jasné, jestli PIN existuje, nic neotvírat (krátké čekání, ťuk mimo = zpět).
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Pro rodiče") },
            text = { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Zrušit") } },
        )
        return
    }
    if (!unlocked && pin != null) {
        SlovoPinDialog(
            profileName = ui.guardProfileName ?: "rodiče",
            onDismiss = onClose,
            onVerified = { unlocked = true },
            verify = { PinHasher.verify(it, pin) },
            title = "Pro rodiče",
            message = "Zadej PIN dospělého profilu (${ui.guardProfileName ?: "rodič"}).",
            confirmLabel = "Otevřít",
        )
        return
    }
    BackHandler(onBack = onClose)
    SlovoParentScreen(
        ui = ui,
        booksCount = booksCount,
        onDisplay = vm::setDisplay,
        onClose = onClose,
        onSelect = onSelect,
    )
}

@Composable
private fun SlovoParentScreen(
    ui: SlovoParentUiState,
    booksCount: Int,
    onDisplay: ((KidsDisplayPrefs) -> KidsDisplayPrefs) -> Unit,
    onClose: () -> Unit,
    onSelect: (SlovoSection) -> Unit,
) {
    val downloadsVm: DownloadsViewModel = hiltViewModel()
    val dl by downloadsVm.state.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět k dítěti")
                }
                Text("Pro rodiče", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ui.guardPinHash == null) {
                    Text(
                        "V appce zatím není PIN, sem se dostane kdokoli. Nastav PIN v Profilu: přepni na dospělého a podrž jeho kartu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                ParentCard(Icons.Rounded.CheckCircle, "Schválené") {
                    val a = ui.approved
                    Text(
                        listOf(
                            cz(a.shows, "pořad", "pořady", "pořadů"),
                            cz(a.series, "série", "série", "sérií"),
                            cz(a.episodes, "díl", "díly", "dílů"),
                            cz(booksCount, "kniha", "knihy", "knih"),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Hint("Co dítě smí poslouchat, přidáváš a odebíráš v Rodičovské appce na webu. Telefon si změny stáhne při spuštění a návratu do appky.")
                }
                ParentCard(Icons.Rounded.Download, "V telefonu", onClick = { onSelect(SlovoSection.STAHOVANI) }) {
                    Text(
                        listOf(
                            "${cz(dl.downloaded.size, "díl", "díly", "dílů")} (${DownloadFormat.bytes(dl.usedBytes)})",
                            "${cz(dl.books.size, "kniha", "knihy", "knih")} (${DownloadFormat.bytes(dl.books.sumOf { it.sizeBytes })})",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Hint("Volno ${DownloadFormat.bytes(dl.freeBytes)}. Ťukni pro seznam stažených.")
                }
                ParentCard(Icons.Rounded.GridView, "Zobrazení pro dítě") {
                    KidsDisplayControls(ui.display, onDisplay)
                }
                ParentCard(Icons.Rounded.ChildCare, "Dětský telefon") {
                    SlovoKioskSection()
                }
                ParentCard(Icons.Rounded.Lock, "Přepnout profil", onClick = { onSelect(SlovoSection.PROFIL) }) {
                    Hint("Na dospělého přes PIN, tam i nastavení PINu.")
                }
                ParentCard(SlovoSection.NASTAVENI.icon, "Nastavení appky", onClick = { onSelect(SlovoSection.NASTAVENI) }) {
                    Hint("Vzhled, zobrazení Domů, verze a aktualizace.")
                }
            }
        }
    }
}

/** Volby zobrazení poličky Dětí — sdílí Pro rodiče i Nastavení dospělého › Děti a profily. */
@Composable
internal fun KidsDisplayControls(
    d: KidsDisplayPrefs,
    onDisplay: ((KidsDisplayPrefs) -> KidsDisplayPrefs) -> Unit,
) {
    SettingChips(
        label = "Velikost obrázků na Poslechu",
        options = listOf(2, 3),
        selected = d.safeColumns,
        labelOf = { if (it == 2) "Velké (2 vedle sebe)" else "Menší (3 vedle sebe)" },
        onSelect = { c -> onDisplay { it.copy(columns = c) } },
    )
    SettingSwitchRow(
        title = "Názvy pod obrázky",
        subtitle = "Vypni pro dítě, které ještě nečte",
        checked = d.showTitles,
        onCheckedChange = { v -> onDisplay { it.copy(showTitles = v) } },
    )
    SettingChips(
        label = "Appka se otevře na",
        options = listOf(KidsDisplayPrefs.START_DOMU, KidsDisplayPrefs.START_POLICKA),
        selected = if (d.start == KidsDisplayPrefs.START_POLICKA) KidsDisplayPrefs.START_POLICKA else KidsDisplayPrefs.START_DOMU,
        labelOf = { if (it == KidsDisplayPrefs.START_POLICKA) SlovoSection.POSLECH.label else SlovoSection.DOMU.label },
        onSelect = { s -> onDisplay { it.copy(start = s) } },
    )
    Hint("Uloží se k profilu Dětí na server, platí na všech jeho telefonech.")
}

/** Nastavení dospělého › Děti a profily › Zobrazení pro Děti (stejné volby jako Pro rodiče). */
@Composable
internal fun SlovoKidsDisplaySection(vm: SlovoParentViewModel = hiltViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    if (!ui.hasKidsProfile) {
        Hint("V appce není dětský profil.")
        return
    }
    KidsDisplayControls(ui.display, vm::setDisplay)
    Hint("V telefonu dítěte je totéž pod Pro rodiče: podrž 3 s levý horní roh a zadej PIN.")
}

@Composable
private fun ParentCard(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                content()
            }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    if (onClick != null) {
        Surface(onClick = onClick, color = color, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) { body() }
    } else {
        Surface(color = color, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) { body() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** „1 pořad · 3 pořady · 5 pořadů" (čeština: 1 / 2–4 / 0 a 5+). */
private fun cz(n: Int, one: String, few: String, many: String): String =
    "$n " + when (n) {
        1 -> one
        in 2..4 -> few
        else -> many
    }
