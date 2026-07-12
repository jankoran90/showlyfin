package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder

/**
 * TENFOOT (SHW-87) F3 — 10-foot řádkové komponenty Nastavení pro TV. Všechno se ovládá D-padem přes
 * fokusovatelná ± tlačítka / přepínač (žádný `Slider` — ten na TV zabere D-pad a nejde z něj vyskočit,
 * user feedback 2026-07-12). Barvy/tvary z motivu (design guard). Sdílené s domovem přes `tvFocusBorder`.
 */

/** Blok Nastavení: nadpis + svislý seznam řádků. */
@Composable
fun TvSettingsBlock(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
        )
        content()
    }
}

/** Přepínač (Bool). Celý řádek je fokusovatelný a D-pad center přepne. */
@Composable
fun TvToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowLabel(label = label, subtitle = subtitle, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onCheckedChange(it) })
    }
}

/**
 * Číselná osa v procentech (0–100, krok [step]). Vpravo [−] „45 %" [+]; obě tlačítka D-pad-fokusovatelná.
 * Ukládá se přes [onPercent] (volající si přepočítá na Float `it/100f`, pokud VM čeká 0f..1f).
 */
@Composable
fun TvValueStepperRow(
    label: String,
    percent: Int,
    onPercent: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 5,
    subtitle: String? = null,
) {
    StepperRow(
        label = label,
        valueLabel = "$percent %",
        subtitle = subtitle,
        onMinus = { onPercent((percent - step).coerceIn(0, 100)) },
        onPlus = { onPercent((percent + step).coerceIn(0, 100)) },
        minusEnabled = percent > 0,
        plusEnabled = percent < 100,
        modifier = modifier,
    )
}

/**
 * Výběr z konečného seznamu voleb ([options]) přes ± cyklení (bez wrap — na krajích se tlačítko ztlumí).
 * Univerzální pro enumy (Pozadí/Motiv) i číselné option-listy (velikost písma, úroveň DRC).
 */
@Composable
fun <T> TvOptionStepperRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val idx = options.indexOf(selected).coerceAtLeast(0)
    StepperRow(
        label = label,
        valueLabel = labelOf(options[idx]),
        subtitle = subtitle,
        onMinus = { if (idx > 0) onSelect(options[idx - 1]) },
        onPlus = { if (idx < options.lastIndex) onSelect(options[idx + 1]) },
        minusEnabled = idx > 0,
        plusEnabled = idx < options.lastIndex,
        modifier = modifier,
    )
}

/** Sdílené tělo stepperu: label vlevo, vpravo [−] hodnota [+]. */
@Composable
private fun StepperRow(
    label: String,
    valueLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowLabel(label = label, subtitle = subtitle, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepperButton(icon = Icons.Filled.Remove, enabled = minusEnabled, onClick = onMinus)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(min = 128.dp),
            )
            StepperButton(icon = Icons.Filled.Add, enabled = plusEnabled, onClick = onPlus)
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .tvFocusBorder(shape = CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .size(28.dp),
    )
}

/**
 * TENFOOT F3 — účet Trakt na TV přes device-code (bez prohlížeče). Zobrazí velký kód + adresu k zadání na
 * jiném zařízení, tlačítko přihlásit/odhlásit je D-pad-fokusovatelné. Stav řídí sdílený [SettingsViewModel].
 */
@Composable
fun TvTraktAccountRow(
    loggedIn: Boolean,
    userCode: String?,
    verificationUrl: String?,
    status: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RowLabel(
                label = "Trakt",
                subtitle = if (loggedIn) "Přihlášeno" else "Sledování historie a oblíbených",
                modifier = Modifier.weight(1f),
            )
            TvActionChip(
                label = when {
                    loggedIn -> "Odhlásit"
                    userCode == null -> "Přihlásit"
                    else -> "Čekám…"
                },
                enabled = loggedIn || userCode == null,
                danger = loggedIn,
                onClick = if (loggedIn) onLogout else onLogin,
            )
        }
        if (!loggedIn && userCode != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Otevři ${verificationUrl ?: "trakt.tv/activate"} na telefonu a zadej kód:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = userCode,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (status != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fokusovatelné akční tlačítko na TV řádku (D-pad center = klik). Barva z motivu, `danger` = chybová. */
@Composable
private fun TvActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val container = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
    val content = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
    val alpha = if (enabled) 1f else 0.4f
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = content.copy(alpha = alpha),
        modifier = modifier
            .tvFocusBorder(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(container.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun RowLabel(label: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
