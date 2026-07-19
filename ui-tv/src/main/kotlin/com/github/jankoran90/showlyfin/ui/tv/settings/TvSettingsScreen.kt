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
import androidx.compose.ui.platform.LocalContext
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
import com.github.jankoran90.showlyfin.feature.detail.DetailActionsPlacement
import com.github.jankoran90.showlyfin.feature.detail.TvDetailLayout
import com.github.jankoran90.showlyfin.ui.phone.DetailPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.FontPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel
import com.github.jankoran90.showlyfin.ui.tv.nav.TvSection
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
    val immersiveHeader by homeVm.immersiveHeader.collectAsStateWithLifecycle()
    val immersiveHeaderLines by homeVm.immersiveHeaderLines.collectAsStateWithLifecycle()
    val allRows by homeVm.allRows.collectAsStateWithLifecycle()
    val activePid by homeVm.activeProfileId.collectAsStateWithLifecycle()
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
                    label = "Immersive pozadí (Netflix styl)",
                    subtitle = "Fanart podle vybrané karty na pozadí — Domů a Objevovat",
                    checked = immersive,
                    onCheckedChange = homeVm::setImmersiveBackground,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                // KOLO2 (M): hlavní vypínač; konkrétní řady se zapínají zvlášť v editoru řady (podržení OK
                // na řadě → „Immersive hlavička"). Z výroby zapnutá jen první řada.
                TvToggleRow(
                    label = "Immersive hlavička (Netflix styl)",
                    subtitle = "Hlavní vypínač. Zapni pro jednotlivé řady v jejich editoru (podržení OK)",
                    checked = immersiveHeader,
                    onCheckedChange = homeVm::setImmersiveHeader,
                )
                // CONVERGE (SHW-97): počet řádků popisu v immersive hlavičce — Auto (dopočet dle výšky/písma,
                // ať se nic neuřízne pod řadami) nebo pevně 1..6.
                TvOptionStepperRow(
                    label = "Řádků popisu v hlavičce",
                    subtitle = "Auto = přizpůsobí se výšce a velikosti písma",
                    options = listOf(0, 1, 2, 3, 4, 5, 6),
                    selected = immersiveHeaderLines,
                    labelOf = { if (it == 0) "Auto" else it.toString() },
                    onSelect = homeVm::setImmersiveHeaderLines,
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
                TvOptionStepperRow(
                    label = "Velikost UI",
                    subtitle = "Zvětší/zmenší celé rozhraní (karty, rozestupy, ikony)",
                    options = FontPrefsViewModel.UI_SCALE_OPTIONS,
                    selected = font.uiScalePct,
                    labelOf = { "$it %" },
                    onSelect = fontPrefs::setUiScalePct,
                )
            }
        }

        // ── Mřížka ── (COUCH DA4: globální šířka karet + rozestupy pro všechny řady i Objevovat)
        item {
            TvSettingsBlock(title = "Mřížka") {
                TvOptionStepperRow(
                    label = "Šířka karet",
                    subtitle = "Širší karta = víc textu, méně sloupců (platí na všechny řady i Objevovat)",
                    options = FontPrefsViewModel.GRID_WIDTH_OPTIONS,
                    selected = font.gridWidthPct,
                    labelOf = { "$it %" },
                    onSelect = fontPrefs::setGridWidthPct,
                )
                TvOptionStepperRow(
                    label = "Rozestupy karet",
                    subtitle = "Mezera mezi kartami v řadě i mřížce",
                    options = FontPrefsViewModel.GRID_SPACING_OPTIONS,
                    selected = font.gridSpacingPct,
                    labelOf = { "$it %" },
                    onSelect = fontPrefs::setGridSpacingPct,
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
                // PASSPORT ③ (SHW-93) — kdy vzdát necachovaný RD zdroj, co se nezačne stahovat (drží 0 %).
                // Raw trakt_prefs (čte i DetailViewModel), vzorem phone DINGO sekce.
                val rdCtx = LocalContext.current
                val rdPrefs = remember { rdCtx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE) }
                var rdStall by remember { mutableStateOf(rdPrefs.getInt("rd_stall_timeout_sec", 120)) }
                TvOptionStepperRow(
                    label = "Vzdát necachovaný zdroj po",
                    subtitle = "Když se na Real-Debrid nezačne stahovat (0 %) za tuhle dobu, appka to vzdá a vyzve k jinému zdroji",
                    options = listOf(5, 10, 30, 60, 120),
                    selected = rdStall,
                    labelOf = { if (it >= 60) "${it / 60} min" else "$it s" },
                    onSelect = { rdStall = it; rdPrefs.edit().putInt("rd_stall_timeout_sec", it).apply() },
                )
                // CATALOGUE — parita s telefonem (FilmyPlayerSection): vynutit SW dekódování. Raw trakt_prefs,
                // konzument sdílený MoviePlayerService (běží i na TV).
                var forceSw by remember {
                    mutableStateOf(rdPrefs.getBoolean(PlayerPrefs.FORCE_SW_DECODER_KEY, PlayerPrefs.DEFAULT_FORCE_SW_DECODER))
                }
                TvToggleRow(
                    label = "Vynutit softwarové dekódování obrazu",
                    subtitle = "Když hardware dekodér selže (černý obraz u HEVC apod.), zkus softwarový. Pomalejší, ale spolehlivější",
                    checked = forceSw,
                    onCheckedChange = { forceSw = it; rdPrefs.edit().putBoolean(PlayerPrefs.FORCE_SW_DECODER_KEY, it).apply() },
                )
                // CATALOGUE — parita s telefonem (FilmyPlayerSection): opt-in autoplay u karty „Uloženo k přehrání".
                // Raw trakt_prefs, čte TvHomeViewModel.autoplayRememberedEnabled() při kliku. Default OFF.
                var autoplayRemembered by remember { mutableStateOf(rdPrefs.getBoolean("autoplay_remembered_enabled", false)) }
                TvToggleRow(
                    label = "Přehrát rovnou u zapamatovaného zdroje",
                    subtitle = "Klik na kartu v řadě „Uloženo k přehrání" spustí přehrávání hned, místo otevření detailu",
                    checked = autoplayRemembered,
                    onCheckedChange = { autoplayRemembered = it; rdPrefs.edit().putBoolean("autoplay_remembered_enabled", it).apply() },
                )
                // CATALOGUE — parita: živě dotahovat uložené zdroje bez restartu (konzument DetailViewModel, i TV).
                // Jen dospělý profil (autoRefreshSourcesAvailable = effectiveAgeCap == null).
                if (sys.autoRefreshSourcesAvailable) {
                    TvToggleRow(
                        label = "Živě dotahovat zdroje",
                        subtitle = "Po otevření detailu sám dohledá uložený zdroj ze serveru, bez restartu appky",
                        checked = sys.autoRefreshSources,
                        onCheckedChange = settings::setAutoRefreshSources,
                    )
                }
            }
        }

        // ── Detail obsahu ── (extrahováno do TvContentSettingsBlocks kvůli stropu 600ř)
        item { TvDetailContentBlock(detail, detailPrefs) }

        // ── Kurátor (AUTEUR SHW-91) — osy mozku „Pro tebe" (jistota↔překvapení, druh, módy) ──
        item { TvCuratorSettingsBlock() }

        // ── Filmotéka (CINEMATHEQUE SHW-90) — zdroje sjednocené plochy + výchozí osa ──
        item { TvFilmotekaSettingsBlock() }

        // ── Vzácné klenoty (LAPIDARY SHW-96) — výběr zemí sekce + řazení ──
        item { TvLapidarySettingsBlock() }

        // ── Rodičovská kontrola (COUCH SHW-88) — věkový strop objevovacích ploch dětského profilu ──
        item { TvParentalSettingsBlock() }

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
                TvTraktImportRow() // COUCH — nahrát zhlédnutou JF historii profilu do jeho Traktu
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
                // CATALOGUE — parita s telefonem: počet položek v řadě na domově (pref UŽ čte TvHomeViewModel,
                // chyběl jen ovladač). Raw trakt_prefs. Projeví se po obnovení domova.
                val homeCtx = LocalContext.current
                val homePrefs = remember { homeCtx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE) }
                var rowLimit by remember { mutableStateOf(homePrefs.getInt("home_row_item_limit", 0)) }
                TvOptionStepperRow(
                    label = "Počet položek v řadě",
                    subtitle = "Kolik titulů se přednačte v každé řadě na domově (víc = delší řady). Projeví se po obnovení domova",
                    options = listOf(0, 20, 30, 40, 50, 60),
                    selected = rowLimit,
                    labelOf = { if (it == 0) "Výchozí" else "$it" },
                    onSelect = { rowLimit = it; homePrefs.edit().putInt("home_row_item_limit", it).apply() },
                )
                // CATALOGUE — parita: per-profil VÝCHOZÍ SEKCE (kam se profil otevře po startu). Pref
                // `tv_default_section_<profileId>`, aplikuje TvShell při startu/přepnutí profilu.
                activePid?.let { pid ->
                    var defSection by remember(pid) {
                        mutableStateOf(
                            homePrefs.getString("tv_default_section_$pid", null)
                                ?.let { runCatching { TvSection.valueOf(it) }.getOrNull() } ?: TvSection.HOME
                        )
                    }
                    TvOptionStepperRow(
                        label = "Výchozí sekce",
                        subtitle = "Kam se tento profil otevře po spuštění appky",
                        options = listOf(
                            TvSection.HOME, TvSection.FILMOTEKA, TvSection.FOR_YOU,
                            TvSection.LAPIDARY, TvSection.LIBRARY, TvSection.WANT_TO_SEE,
                        ),
                        selected = defSection,
                        labelOf = ::tvSectionLabel,
                        onSelect = { defSection = it; homePrefs.edit().putString("tv_default_section_$pid", it.name).apply() },
                    )
                }
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

        // ── Řady knihovny (CONVERGE V1: řazení + skrývání knihoven v sekci Knihovna) ──
        item { TvLibraryRowsSettingsBlock(libraries = libraries) }

        // ── Řady Traktu (CONVERGE V1: řazení + skrývání řad sekce Trakt) ──
        item { TvTraktRowsSettingsBlock() }

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

/** CATALOGUE — český popisek sekce pro výběr „Výchozí sekce" (parita s telefonem). */
private fun tvSectionLabel(s: TvSection): String = when (s) {
    TvSection.HOME -> "Domů"
    TvSection.FOR_YOU -> "Pro tebe"
    TvSection.FILMOTEKA -> "Filmotéka"
    TvSection.LAPIDARY -> "Vzácné klenoty"
    TvSection.LIBRARY -> "Knihovna"
    TvSection.WANT_TO_SEE -> "Chci vidět"
    TvSection.TRAKT -> "Trakt"
    TvSection.WATCHLIST -> "Oblíbené"
    TvSection.SETTINGS -> "Nastavení"
}
