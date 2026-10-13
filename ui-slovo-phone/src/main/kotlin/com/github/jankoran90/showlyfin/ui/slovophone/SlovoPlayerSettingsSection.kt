package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.QueueGestureSettingsViewModel

/** Nastavení → Přehrávač: co dělá swipe položky fronty v přehrávači (každý směr zvlášť, výchozí vypnuto). */
@Composable
internal fun SlovoPlayerSettingsSection(viewModel: QueueGestureSettingsViewModel = hiltViewModel()) {
    val left by viewModel.left.collectAsStateWithLifecycle()
    val right by viewModel.right.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingChips(
            label = "Fronta: swipe doleva",
            subtitle = "Vypnuto = položka fronty se tažením nehne (žádné omylem odebrané díly).",
            options = QueueGestureSettingsViewModel.OPTIONS,
            selected = left,
            labelOf = QueueGestureSettingsViewModel::label,
            onSelect = viewModel::setLeft,
        )
        SettingChips(
            label = "Fronta: swipe doprava",
            options = QueueGestureSettingsViewModel.OPTIONS,
            selected = right,
            labelOf = QueueGestureSettingsViewModel::label,
            onSelect = viewModel::setRight,
        )
    }
}
