package com.github.jankoran90.showlyfin.core.appservices.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.core.appservices.services.PendingUpdate
import com.github.jankoran90.showlyfin.core.appservices.services.UpdateChecker
import com.github.jankoran90.showlyfin.core.appservices.services.UpdatePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

object UpdateOverlayController {
    private val _pending = MutableStateFlow<PendingUpdate?>(null)
    val pending: StateFlow<PendingUpdate?> = _pending.asStateFlow()

    fun show(context: Context) {
        _pending.value = UpdatePreferences.read(context)
    }

    fun dismiss() {
        _pending.value = null
    }
}

@Composable
fun UpdateOverlayHost() {
    val context = LocalContext.current
    val pending by UpdateOverlayController.pending.collectAsState()
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    val progress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        if (UpdatePreferences.read(context) != null) {
            UpdateOverlayController.show(context)
        }
    }

    val current = pending ?: return
    UpdateAvailableDialog(
        tagName = current.versionName,
        body = current.notes,
        isDownloading = isDownloading,
        downloadProgress = progress.floatValue,
        onConfirm = {
            scope.launch {
                isDownloading = true
                progress.floatValue = 0f
                val checker = UpdateChecker()
                val file = checker.downloadApk(context, current.toManifest()) { progress.floatValue = it }
                isDownloading = false
                if (file != null) {
                    runCatching {
                        val intent = checker.buildInstallIntent(context, file)
                        context.startActivity(intent)
                    }.onFailure { Timber.w(it, "install intent failed") }
                    UpdateOverlayController.dismiss()
                }
            }
        },
        onDismiss = {
            UpdateOverlayController.dismiss()
        },
    )
}
