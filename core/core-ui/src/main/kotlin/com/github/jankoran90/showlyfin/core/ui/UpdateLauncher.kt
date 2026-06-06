package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.compositionLocalOf

interface UpdateLauncher {
    fun checkNow(onResult: (UpdateCheckResult) -> Unit)
    fun lastCheckAt(): Long
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
