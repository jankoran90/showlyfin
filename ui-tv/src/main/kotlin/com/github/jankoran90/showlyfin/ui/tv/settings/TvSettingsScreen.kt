package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.LibrarySummary
import com.github.jankoran90.showlyfin.core.domain.home.SidebarEntry
import com.github.jankoran90.showlyfin.core.domain.home.SidebarItem
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.home.TvAddRowPicker
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeRowEditor
import com.github.jankoran90.showlyfin.ui.phone.DetailPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.FontPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel
import com.github.jankoran90.showlyfin.ui.phone.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.theme.Background
import com.github.jankoran90.showlyfin.ui.phone.theme.ShowlyfinSkin
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * TENFOOT (SHW-87) F3 — nativní 10-foot Nastavení na TV. Sdílí tytéž ViewModely co telefon
 * ([ThemePrefsViewModel], [FontPrefsViewModel], [SettingsViewModel]) → změny se projeví napříč
 * (prefs jsou activity-scoped, náhled naživo). Vše přes D-pad ± steppery / přepínače, žádný `Slider`.
 *
 * Zaměřeno na to, co člověk ladí u televize: vzhled (motiv, barevné osy, písmo), obraz/zvuk (DRC filmu),
 * přehrávač (transport lišta), účty (Trakt přes device-code — bez prohlížeče), systém. Pokročilé profilové
 * věci zůstávají v telefonním Nastavení.
 */

/** Styly karet pro sekce detailu (kolekce/režisér/studio) — jen ty, co v horizontálním pásu dávají smysl. */
private val DETAIL_SECTION_STYLES = listOf(
    HomeCardStyle.POSTER,
    HomeCardStyle.LANDSCAPE,
    HomeCardStyle.FANART_DETAIL,
)

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    themePrefs: ThemePrefsViewModel = hiltViewModel(),
    fontPrefs: FontPrefsViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
    detailPrefs: DetailPrefsViewModel = hiltViewModel(),
    homeVm: TvHomeViewModel = hiltViewModel(),
    libraryVm: LibraryRowsViewModel = hiltViewModel(),
) {
    val theme by themePrefs.state.collectAsStateWithLifecycle()
    val font by fontPrefs.state.collectAsStateWithLifecycle()
    val sys by settings.uiState.collectAsStateWithLifecycle()
    val detail by detailPrefs.state.collectAsStateWithLifecycle()
    val sidebar by homeVm.sidebar.collectAsStateWithLifecycle()
    val immersive by homeVm.immersiveBackground.collectAsStateWithLifecycle()
    val allRows by homeVm.allRows.collectAsStateWithLifecycle()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var importMsg by remember { mutableStateOf<String?>(null) }

    // Dedikovaný editor řad domova z Nastavení (root cause fix: ovladač boxu nemá klávesu Menu, kterou se
    // spouštěl inline editor na domově). Overlay + picker jsou stejné komponenty jako na domově.
    LaunchedEffect(Unit) { libraryVm.load() }
    val libraries = remember(libraryState.rows) {
        libraryState.rows.map { LibrarySummary(it.libraryId, it.libraryName, it.collectionType) }
    }
    var editingRowId by remember { mutableStateOf<String?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }
    var pendingNewId by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    // TENFOOT: po OK ze sidebaru zaostři PRVNÍ interaktivní řádek (immersive toggle), ne šipku Zpět.
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = true,
        isTargetPlaced = { listState.layoutInfo.visibleItemsInfo.any { it.index >= 1 } },
    )

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().tvOverscan(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zpět",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .tvFocusBorder(shape = CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(8.dp),
                )
                Text("Nastavení", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        // ── Vzhled ──
        item {
            TvSettingsBlock(title = "Vzhled") {
                TvToggleRow(
                    label = "Immersive pozadí",
                    subtitle = "Fanart podle vybrané karty (Netflix styl) na Domů a Objevovat",
                    checked = immersive,
                    onCheckedChange = homeVm::setImmersiveBackground,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                TvOptionStepperRow(
                    label = "Pozadí",
                    options = Background.entries.toList(),
                    selected = theme.background,
                    labelOf = { it.displayName },
                    onSelect = themePrefs::setBackground,
                )
                TvOptionStepperRow(
                    label = "Motiv",
                    subtitle = "Barevné schéma akcentu",
                    options = ShowlyfinSkin.entries.toList(),
                    selected = theme.skin,
                    labelOf = { it.displayName },
                    onSelect = themePrefs::setSkin,
                )
                TvValueStepperRow(
                    label = "Tónování ploch",
                    percent = (theme.surfaceTint * 100).roundToInt(),
                    onPercent = { themePrefs.setSurfaceTint(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Světlost ploch",
                    percent = (theme.surfaceLightness * 100).roundToInt(),
                    onPercent = { themePrefs.setSurfaceLightness(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Síla akcentu",
                    percent = (theme.accentStrength * 100).roundToInt(),
                    onPercent = { themePrefs.setAccentStrength(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Tónování prvků",
                    percent = (theme.containerTint * 100).roundToInt(),
                    onPercent = { themePrefs.setContainerTint(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Kontrast textu",
                    percent = (theme.textContrast * 100).roundToInt(),
                    onPercent = { themePrefs.setTextContrast(it / 100f) },
                )
                TvValueStepperRow(
                    label = "Sytost barev",
                    percent = (theme.accentChroma * 100).roundToInt(),
                    onPercent = { themePrefs.setAccentChroma(it / 100f) },
                )
            }
        }

        // ── Písmo ──
        item {
            TvSettingsBlock(title = "Písmo") {
                TvToggleRow(
                    label = "Patkové písmo",
                    subtitle = "Newsreader místo systémového",
                    checked = font.serif,
                    onCheckedChange = fontPrefs::setSerif,
                )
                if (font.serif) {
                    TvToggleRow(
                        label = "Jen na nadpisy",
                        checked = font.headingOnly,
                        onCheckedChange = fontPrefs::setHeadingOnly,
                    )
                }
                TvOptionStepperRow(
                    label = "Velikost písma",
                    options = FontPrefsViewModel.SCALE_OPTIONS,
                    selected = font.scalePct,
                    labelOf = { "$it %" },
                    onSelect = fontPrefs::setScalePct,
                )
            }
        }

        // ── Obraz a zvuk ──
        item {
            TvSettingsBlock(title = "Obraz a zvuk") {
                TvOptionStepperRow(
                    label = "Normalizace hlasitosti filmu",
                    subtitle = "Ztlumí hlasité scény, zesílí ticho",
                    options = listOf(0, 1, 2, 3),
                    selected = sys.movieDrcLevel,
                    labelOf = ::drcLabel,
                    onSelect = settings::setMovieDrcLevel,
                )
                TvToggleRow(
                    label = "Passthrough zvuku (5.1 do AVR)",
                    subtitle = "Přijímač dekóduje zvuk sám — opraví rozjetý zvuk/obraz. Vypni, jen když je ticho",
                    checked = sys.playerTvAudioPassthrough,
                    onCheckedChange = settings::setPlayerTvAudioPassthrough,
                )
            }
        }

        // ── Přehrávač ──
        item {
            TvSettingsBlock(title = "Přehrávač") {
                TvOptionStepperRow(
                    label = "Skrýt ovládání po",
                    subtitle = "Nečinnost, po které zmizí lišta přehrávače",
                    options = PlayerPrefs.CONTROLS_HIDE_SEC_OPTIONS,
                    selected = sys.playerControlsHideSec,
                    labelOf = ::hideDelayLabel,
                    onSelect = settings::setPlayerControlsHideSec,
                )
                TvOptionStepperRow(
                    label = "Krok převíjení",
                    subtitle = "O kolik posunou tlačítka ⏮ ⏭ a osa",
                    options = PlayerPrefs.SEEK_STEP_SEC_OPTIONS,
                    selected = sys.playerSeekStepSec,
                    labelOf = { "$it s" },
                    onSelect = settings::setPlayerSeekStepSec,
                )
            }
        }

        // ── Detail obsahu ── (TENFOOT WS-B/WS-C: volitelné sekce karty filmu/seriálu)
        item {
            TvSettingsBlock(title = "Detail obsahu") {
                TvToggleRow(
                    label = "Tvůrci",
                    subtitle = "Pás herců a režie + Scénář/Kamera/Žánry",
                    checked = detail.showCreators,
                    onCheckedChange = detailPrefs::setCreators,
                )
                TvToggleRow(
                    label = "Sezóny a epizody",
                    subtitle = "U seriálu výběr sezóny a seznam epizod v detailu",
                    checked = detail.showSeasons,
                    onCheckedChange = detailPrefs::setSeasons,
                )
                TvToggleRow(
                    label = "Kolekce",
                    subtitle = "Další díly ságy / kolekce",
                    checked = detail.showCollections,
                    onCheckedChange = detailPrefs::setCollections,
                )
                TvToggleRow(
                    label = "Od stejného režiséra",
                    checked = detail.showDirector,
                    onCheckedChange = detailPrefs::setDirector,
                )
                TvToggleRow(
                    label = "Od stejného studia",
                    checked = detail.showStudio,
                    onCheckedChange = detailPrefs::setStudio,
                )
                TvOptionStepperRow(
                    label = "Styl karet sekcí",
                    subtitle = "Kolekce / režisér / studio: plakát, fanart nebo fanart s popisem",
                    options = DETAIL_SECTION_STYLES,
                    selected = detail.sectionStyle,
                    labelOf = { it.label },
                    onSelect = detailPrefs::setSectionStyle,
                )
            }
        }

        // ── Účty ──
        item {
            TvSettingsBlock(title = "Účty") {
                TvTraktAccountRow(
                    loggedIn = sys.traktLoggedIn,
                    userCode = sys.traktUserCode,
                    verificationUrl = sys.traktVerificationUrl,
                    status = sys.traktStatus,
                    onLogin = settings::startTraktDeviceLogin,
                    onLogout = settings::logout,
                )
            }
        }

        // ── Domů / Postranní menu ──
        item {
            TvSettingsBlock(title = "Domů a postranní menu") {
                Text(
                    text = "Řady domova (Pokračovat, knihovny, kolekce, Uloženo k přehrání, Objevovat…) uprav tady " +
                        "níže, nebo přímo na domově podržením tlačítka OK na řadě — styl karet, řazení, popisky, " +
                        "přesun, skrytí i přidání nové řady z libovolného zdroje.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = "Řady domova",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                allRows.forEach { row ->
                    HomeRowManageRow(
                        title = row.resolvedTitle(),
                        enabled = row.enabled,
                        styleLabel = row.cardStyle.label,
                        onEdit = { editingRowId = row.id },
                    )
                }
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    TvActionChip(
                        label = "Přidat řadu",
                        enabled = true,
                        onClick = {
                            pendingNewId = "custom_${System.currentTimeMillis()}"
                            showAddPicker = true
                        },
                    )
                }
                Text(
                    text = "Postranní menu",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                sidebar.forEachIndexed { index, entry ->
                    SidebarEditorRow(
                        entry = entry,
                        index = index,
                        count = sidebar.size,
                        onMove = { up -> homeVm.moveSidebar(entry.item, up) },
                        onToggle = { on -> homeVm.setSidebarEnabled(entry.item, on) },
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TvActionChip(
                        label = "Načíst domov ze serveru",
                        enabled = true,
                        onClick = {
                            scope.launch {
                                importMsg = "Načítám ze serveru…"
                                val added = runCatching { homeVm.importFromJellyfin() }.getOrDefault(0)
                                importMsg = when {
                                    added > 0 -> "Naimportováno $added řad z Jellyfinu"
                                    else -> "Nic k importu (server nemá home konfiguraci nebo už je vše přidáno)"
                                }
                            }
                        },
                    )
                    TvActionChip(
                        label = "Obnovit výchozí domov",
                        enabled = true,
                        onClick = { homeVm.resetRows() },
                    )
                }
                importMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // ── Systém ──
        item {
            TvSettingsBlock(title = "Systém") {
                TvToggleRow(
                    label = "Živé logování",
                    subtitle = "Odesílá diagnostické logy na server",
                    checked = sys.liveLogging,
                    onCheckedChange = settings::setLiveLogging,
                )
            }
        }
    }

        // Overlay editoru řady domova (stejné komponenty jako inline editor na domově).
        editingRowId?.let { id ->
            val cfg = allRows.firstOrNull { it.id == id } ?: return@let
            val pos = allRows.indexOfFirst { it.id == id }
            TvHomeRowEditor(
                config = cfg,
                canMoveUp = pos > 0,
                canMoveDown = pos in 0 until allRows.lastIndex,
                hiddenRows = allRows.filter { !it.enabled },
                onUpdate = { homeVm.updateRow(it) },
                onMove = { up -> homeVm.moveRow(id, up) },
                onHide = { homeVm.setRowEnabled(id, false); editingRowId = null },
                onUnhide = { unhideId -> homeVm.setRowEnabled(unhideId, true) },
                onAddRow = {
                    pendingNewId = "custom_${System.currentTimeMillis()}"
                    editingRowId = null
                    showAddPicker = true
                },
                onDismiss = { editingRowId = null },
            )
        }

        if (showAddPicker) {
            TvAddRowPicker(
                libraries = libraries,
                newId = pendingNewId,
                onPick = { newRow ->
                    homeVm.addRow(newRow)
                    showAddPicker = false
                    editingRowId = newRow.id
                },
                onDismiss = { showAddPicker = false },
            )
        }
    }
}

/** Řádek správy řady domova: název + aktuální styl + stav (skrytá) + chip „Upravit" (otevře plný editor). */
@Composable
private fun HomeRowManageRow(
    title: String,
    enabled: Boolean,
    styleLabel: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = if (enabled) styleLabel else "$styleLabel · skrytá",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TvActionChip(label = "Upravit", enabled = true, onClick = onEdit)
    }
}

/** Řádek editoru sidebaru: název sekce + přesun ↑/↓ + zap/vyp (vše D-pad chipy). */
@Composable
private fun SidebarEditorRow(
    entry: SidebarEntry,
    index: Int,
    count: Int,
    onMove: (up: Boolean) -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val item = SidebarItem.fromName(entry.item) ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TvActionChip(label = "↑", enabled = index > 0, onClick = { onMove(true) })
        TvActionChip(label = "↓", enabled = index < count - 1, onClick = { onMove(false) })
        TvActionChip(
            label = if (entry.enabled) "Zap" else "Vyp",
            enabled = true,
            danger = !entry.enabled,
            onClick = { onToggle(!entry.enabled) },
        )
    }
}

private fun drcLabel(level: Int): String = when (level) {
    0 -> "Vypnuto"
    1 -> "Mírná"
    2 -> "Střední"
    else -> "Noční"
}

private fun hideDelayLabel(sec: Int): String = if (sec <= 0) "Nikdy" else "$sec s"
