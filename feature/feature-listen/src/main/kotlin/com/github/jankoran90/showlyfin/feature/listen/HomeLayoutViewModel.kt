package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Nastavení → „Zobrazení Domů" — viz [HomeLayoutPrefs]. */
@HiltViewModel
class HomeLayoutViewModel @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    val layout: StateFlow<HomeLayout> = HomeLayoutPrefs.observe(prefs)
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayoutPrefs.read(prefs))

    fun update(transform: (HomeLayout) -> HomeLayout) {
        HomeLayoutPrefs.write(prefs, transform(HomeLayoutPrefs.read(prefs)))
    }
}
