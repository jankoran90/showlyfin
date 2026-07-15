package com.github.jankoran90.showlyfin.ui.tv.trakt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.trakt.TraktSessionSignal
import com.github.jankoran90.showlyfin.ui.tv.settings.TvActionChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * CONVERGE V1 — sleduje [TraktSessionSignal] (definitivní odhlášení z Traktu = mrtvý refresh_token) a řekne
 * TV shellu, ať zobrazí globální re-auth prompt. Uzavírá dřívější Known gap (jen matoucí „HTTP 401" v detailu).
 */
@HiltViewModel
class TvReauthPromptViewModel @Inject constructor(
    signal: TraktSessionSignal,
) : ViewModel() {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    init {
        // drop(1) = ignoruj iniciální hodnotu StateFlow; reaguj jen na skutečné odhlášení.
        signal.reauthNeeded.drop(1).onEach { _visible.value = true }.launchIn(viewModelScope)
    }

    fun dismiss() {
        _visible.value = false
    }
}

/**
 * Globální overlay „Trakt tě odhlásil" (vzor [com.github.jankoran90.showlyfin.ui.tv.profile.TvProfileSwitcher]).
 * Jeden klik „Přihlásit znovu" tě hodí do Nastavení → Účty, kde je zavedený device-login (žádná duplikace UI).
 */
@Composable
fun TvTraktReauthDialog(
    onRelogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { panelFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .focusRequester(panelFocus)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Trakt tě odhlásil",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Přihlášení k Traktu vypršelo a nešlo ho automaticky obnovit. " +
                    "Přihlas se prosím znovu — jinak Trakt watchlist, hodnocení a doporučení nepojedou.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvActionChip(label = "Přihlásit znovu", enabled = true, onClick = onRelogin)
                TvActionChip(label = "Zavřít", enabled = true, onClick = onDismiss)
            }
        }
    }
}
