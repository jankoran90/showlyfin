package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.feature.detail.DetailUiState
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel
import java.util.Calendar

/**
 * TENFOOT (SHW-87) Fáze 2 — TV akční řada karty (10-foot). Telefonní hustý top-bar (moc tlačítek)
 * nahrazen: kontextové PRIMÁRNÍ tlačítko s auto-fokusem, pár sekundárních, zbytek pod „Více".
 *
 * Primární: dostupný zdroj (v knihovně / zapamatovaný / staženo) → **Přehrát**; jinak → **Hledat zdroje**.
 * VM se NEMĚNÍ — voláme tytéž funkce co telefon; výběr zdroje otevřou sdílené sheety (StreamPicker přes
 * AdaptivePickerScaffold = už D-pad). Přehrání zapamatovaného jde přes `playStream` → pendingPlaybackUrl →
 * (sdílený LaunchedEffect v DetailScreen) → onPlayStreamUrl → TV přehrávač.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TvDetailActions(
    uiState: DetailUiState,
    viewModel: DetailViewModel,
    onPlayJellyfin: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    // CONVERGE (SHW-97) — D-pad doleva od nejlevější akce (primární CTA) → vysune navigační sidebar (výběr
    // sekce/Nastavení). null = feature vypnutá (telefon / route bez shellu). Název historický (callback řeší
    // navigátor).
    onOpenSettings: (() -> Unit)? = null,
) {
    val inLibrary = uiState.isOwnedInLibrary && uiState.ownedJellyfinId != null
    val hasRemembered = uiState.rememberedSource != null
    val hasSource = inLibrary || hasRemembered
    val isMovie = uiState.item?.type == MediaType.MOVIE
    val canDevice = isMovie && hasSource
    val downloaded = uiState.offlineState.status == OfflineStatus.DOWNLOADED
    val downloading = uiState.offlineState.status == OfflineStatus.DOWNLOADING ||
        uiState.offlineState.status == OfflineStatus.QUEUED

    var showMore by remember { mutableStateOf(false) }
    val primaryFocus = remember { FocusRequester() }
    // CONVERGE V1 — sleduj fokus primárního CTA, ať D-pad doleva JEN z něj (nejlevější akce) skočí do Nastavení.
    var primaryFocused by remember { mutableStateOf(false) }
    // Po otevření karty rovnou zaostři primární CTA (paritně s přehrávačem).
    LaunchedEffect(hasSource) { runCatching { primaryFocus.requestFocus() } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // KOLO2 (L): akční řada = fokusová skupina; vstup z JAKÉHOKOLI směru (D-pad dolů z chipů Recenze/
        // Galerie, nebo z popisu) přistane VŽDY na primárním CTA (Přehrát/Hledat zdroje), ne na náhodném
        // geometricky nejbližším tlačítku (dřív „z Recenze dolů skočilo na Oblíbené").
        Row(
            modifier = Modifier
                .focusGroup()
                .focusProperties { enter = { primaryFocus } }
                // CONVERGE V1 — D-pad doleva, když je fokus na primárním CTA (vlevo už nic není) → Nastavení.
                // Preview na řadě odchytí dřív než fokus; gate `primaryFocused` = nekříží se s posunem
                // vlevo z ostatních tlačítek (ta normálně přejdou na primární CTA).
                .onPreviewKeyEvent { e ->
                    if (onOpenSettings != null && primaryFocused &&
                        e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft
                    ) {
                        onOpenSettings(); true
                    } else {
                        false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Box jen kvůli sledování fokusu primárního CTA (obě varianty sdílí primaryFocus).
            // hasFocus (ne isFocused) = Box sám není fokusovatelný, fokus drží tlačítko uvnitř.
            Box(Modifier.onFocusChanged { primaryFocused = it.hasFocus }) {
                if (hasSource) {
                    TvActionButton(
                        icon = Icons.Filled.PlayArrow,
                        label = "Přehrát",
                        primary = true,
                        focusRequester = primaryFocus,
                        onClick = {
                            when {
                                inLibrary -> uiState.ownedJellyfinId?.let { onPlayJellyfin?.invoke(it) }
                                hasRemembered -> uiState.rememberedSource?.let { viewModel.playStream(it) }
                            }
                        },
                    )
                } else {
                    TvActionButton(
                        icon = Icons.Filled.Search,
                        label = "Hledat zdroje",
                        primary = true,
                        focusRequester = primaryFocus,
                        onClick = { viewModel.openStreamPathChooser() },
                    )
                }
            }

            if (canDevice) {
                TvActionButton(
                    icon = if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    label = when {
                        downloaded -> "Staženo"
                        downloading -> "Stahuje se…"
                        else -> "Stáhnout"
                    },
                    primary = false,
                    onClick = { viewModel.openDownloadMenu() },
                )
            }
            if (isMovie) {
                TvActionButton(
                    icon = if (uiState.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    label = if (uiState.isFavorite) "Oblíbené" else "Do oblíbených",
                    primary = false,
                    active = uiState.isFavorite,
                    onClick = { viewModel.toggleFavorite() },
                )
            }
            TvActionButton(
                icon = if (uiState.isInWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                label = if (uiState.isInWatchlist) "V seznamu" else "Chci vidět",
                primary = false,
                active = uiState.isInWatchlist,
                onClick = { viewModel.toggleWatchlist() },
            )
            if (hasRemembered) {
                TvActionButton(
                    icon = Icons.Filled.MoreHoriz,
                    label = "Více",
                    primary = false,
                    onClick = { showMore = !showMore },
                )
            }
        }

        // Overflow „Více" — vzácné akce (jen se zapamatovaným zdrojem), ať hlavní řada není přeplácaná.
        if (showMore && hasRemembered) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Parita s telefonem (⋮ „Vybrat jiný zdroj") — TÝŽ sdílený picker (⭐ pin + chipy, D-pad).
                TvActionButton(
                    icon = Icons.Filled.SwapHoriz,
                    label = "Vybrat jiný zdroj",
                    primary = false,
                    onClick = { viewModel.openStreamPicker() },
                )
                TvActionButton(
                    icon = Icons.Filled.Delete,
                    label = "Odebrat zdroj",
                    primary = false,
                    danger = true,
                    onClick = { viewModel.removeRememberedSource() },
                )
            }
        }
    }
}

/** Jedno 10-foot akční tlačítko: text+ikona, D-pad fokusovatelné, prstenec z core-ui, barvy z theme. */
@Composable
private fun TvActionButton(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    active: Boolean = false,
    danger: Boolean = false,
) {
    val shape = MaterialTheme.shapes.medium
    val bg = when {
        primary -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        primary -> MaterialTheme.colorScheme.onPrimary
        danger -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // POZOR: tvFocusBorder (uvnitř onFocusChanged) MUSÍ být PŘED clickable, jinak fokus nepozoruje
            // a záře se nevykreslí (stejná past jako u TvMediaCard). Záře je akcentní a kreslí se VEN na pozadí
            // (ne na tlačítku), takže na primárním tlačítku nesplyne — barvu neřešíme, jede default z motivu.
            .tvFocusBorder(shape = shape)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * „Skončí ve 21:00" — aktuální čas + délka filmu (min). Jen film s runtime; jinak null.
 * Calendar (ne java.time) kvůli minSdk 23 bez závislosti na desugaringu.
 */
internal fun endTimeLabel(runtimeMin: Int?): String? {
    if (runtimeMin == null || runtimeMin <= 0) return null
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, runtimeMin)
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    return "Skončí ve %02d:%02d".format(h, m)
}
