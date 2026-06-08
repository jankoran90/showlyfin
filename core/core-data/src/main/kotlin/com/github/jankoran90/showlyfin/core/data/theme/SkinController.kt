package com.github.jankoran90.showlyfin.core.data.theme

import com.github.jankoran90.showlyfin.core.domain.theme.DarkMode
import com.github.jankoran90.showlyfin.core.domain.theme.ShowlyfinSkin
import com.github.jankoran90.showlyfin.core.domain.theme.SkinPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jediný zdroj pravdy o vzhledu pro celou aplikaci (Plan PRISM Fáze 1). Hilt singleton —
 * theme wrappery (phone i TV) konzumují [state], Nastavení volá settery (Fáze 4/5).
 * `SharingStarted.Eagerly` → skin je připravený hned při startu (žádný flash defaultu).
 */
@Singleton
class SkinController @Inject constructor(
    private val repository: ThemeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<ShowlyfinSkin> = repository.skin.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ShowlyfinSkin.DEFAULT,
    )

    fun setPreset(preset: SkinPreset) = scope.launch { repository.setPreset(preset) }
    fun setCustomSeed(seedColor: Long) = scope.launch { repository.setCustomSeed(seedColor) }
    fun setDarkMode(mode: DarkMode) = scope.launch { repository.setDarkMode(mode) }
    fun setDynamicColor(enabled: Boolean) = scope.launch { repository.setDynamicColor(enabled) }
    fun setAmoled(enabled: Boolean) = scope.launch { repository.setAmoled(enabled) }
    fun setContrast(value: Float) = scope.launch { repository.setContrast(value) }
    fun setFontScale(value: Float) = scope.launch { repository.setFontScale(value) }
}
