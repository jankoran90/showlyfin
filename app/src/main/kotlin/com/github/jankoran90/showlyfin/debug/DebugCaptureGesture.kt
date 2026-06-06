package com.github.jankoran90.showlyfin.debug

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 800L

@Composable
fun DebugCaptureGestureHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (isActive) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val activeCount = event.changes.count { it.pressed }
                    if (activeCount >= 3) {
                        val startTime = System.currentTimeMillis()
                        var fired = false
                        while (isActive && !fired) {
                            val next = awaitPointerEvent(PointerEventPass.Initial)
                            val count = next.changes.count { it.pressed }
                            if (count < 3) break
                            if (System.currentTimeMillis() - startTime >= LONG_PRESS_MS) {
                                fired = true
                                val activity = context as? Activity
                                if (activity != null) {
                                    scope.launch {
                                        Toast.makeText(context, "Posílám screenshot…", Toast.LENGTH_SHORT).show()
                                        val ok = DebugCaptureManager.captureAndUpload(activity)
                                        val msg = if (ok) "Screenshot + log odeslán" else "Odeslání selhalo"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                // suppress consume to avoid breaking child gestures
                            } else {
                                delay(60)
                            }
                        }
                    }
                }
            }
        },
    ) { content() }
}
