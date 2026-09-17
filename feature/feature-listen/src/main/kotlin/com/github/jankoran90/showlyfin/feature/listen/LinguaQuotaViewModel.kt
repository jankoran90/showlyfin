package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named

/**
 * LINGUA-YT VLNY (Slovo, user 2026-09-18) — limit 5h mozek kvóty pro AI překlad YouTube titulků
 * (viz [PlayerPrefs.LINGUA_QUOTA_LIMIT_KEY]). Stejné `traktPreferences` jako ostatní player prefs.
 */
@HiltViewModel
class LinguaQuotaViewModel @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private fun normalize(v: Int) = if (v in PlayerPrefs.LINGUA_QUOTA_LIMIT_OPTIONS) v else PlayerPrefs.DEFAULT_LINGUA_QUOTA_LIMIT
    private fun read() = normalize(prefs.getInt(PlayerPrefs.LINGUA_QUOTA_LIMIT_KEY, PlayerPrefs.DEFAULT_LINGUA_QUOTA_LIMIT))

    private val _quotaLimit = MutableStateFlow(read())
    val quotaLimit: StateFlow<Int> = _quotaLimit.asStateFlow()

    fun setQuotaLimit(value: Int) {
        prefs.edit { putInt(PlayerPrefs.LINGUA_QUOTA_LIMIT_KEY, normalize(value)) }
        _quotaLimit.value = read()
    }
}
