package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.compositionLocalOf

interface UpdateLauncher {
    fun checkNow(onResult: (UpdateCheckResult) -> Unit)
    fun lastCheckAt(): Long

    // Plan EVERGREEN (SHW-64) — stav + ruční instalace + konfigurace auto-aktualizací z Nastavení.
    /** versionName připravené nové verze, nebo null když je appka aktuální. */
    fun availableVersion(): String? = null
    /** Stáhni a nainstaluj připravenou novou verzi hned (otevře dialog / tichou instalaci). */
    fun installNow() {}
    fun isAutoUpdateEnabled(): Boolean = true
    fun setAutoUpdateEnabled(value: Boolean) {}
    fun isSilentInstall(): Boolean = true
    fun setSilentInstall(value: Boolean) {}
    fun isWifiOnly(): Boolean = false
    fun setWifiOnly(value: Boolean) {}
}

sealed class UpdateCheckResult {
    data class Available(val tagName: String) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object Failed : UpdateCheckResult()
}

val LocalUpdateLauncher = compositionLocalOf<UpdateLauncher> {
    object : UpdateLauncher {
        override fun checkNow(onResult: (UpdateCheckResult) -> Unit) {
            onResult(UpdateCheckResult.Failed)
        }
        override fun lastCheckAt(): Long = 0L
    }
}

interface DebugCaptureLauncher {
    fun captureNow(onResult: (Boolean) -> Unit)
}

val LocalDebugCaptureLauncher = compositionLocalOf<DebugCaptureLauncher> {
    object : DebugCaptureLauncher {
        override fun captureNow(onResult: (Boolean) -> Unit) {
            onResult(false)
        }
    }
}
