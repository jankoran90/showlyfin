package com.github.jankoran90.showlyfin.debug

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 800L

@Composable
fun DebugCaptureGestureHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val outerScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                var fireJob: Job? = null
                while (isActive) {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { it.pressed }
                    if (pressedCount >= 3) {
                        if (fireJob?.isActive != true) {
                            fireJob = outerScope.launch {
                                delay(LONG_PRESS_MS)
                                val activity = context as? Activity ?: return@launch
                                Toast.makeText(context, "Posílám screenshot…", Toast.LENGTH_SHORT).show()
                                val ok = DebugCaptureManager.captureAndUpload(activity)
                                Toast.makeText(
                                    context,
                                    if (ok) "Screenshot + log odeslán" else "Odeslání selhalo",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    } else {
                        fireJob?.cancel()
                        fireJob = null
                    }
                }
            }
        },
    ) { content() }
}
