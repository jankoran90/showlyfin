package com.github.jankoran90.showlyfin.feature.listen.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AMPHORA (SHW-124) F4 / PR3 — barva přehrávače z obalu. Obal se načte jako zmenšenina 32 px (z cache
 * Coilu) a spočítá se z něj výrazná barva: průměr pixelů vážený sytostí, bez skoro černé, bílé a šedé.
 * U černobílých / tmavých obalů vrátí null (přehrávač zůstane v barvách motivu).
 */
@Composable
fun rememberCoverTint(url: String?, enabled: Boolean): Color? {
    val ctx = LocalContext.current
    var tint by remember(url, enabled) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, enabled) {
        if (!enabled || url.isNullOrBlank()) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(url).size(32).allowHardware(false).build()
        val bmp = (ctx.imageLoader.execute(req) as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        tint = withContext(Dispatchers.Default) { dominantColor(bmp) }
    }
    return tint
}

private fun dominantColor(bmp: Bitmap): Color? {
    val hsv = FloatArray(3)
    var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
    val stepX = (bmp.width / 24).coerceAtLeast(1)
    val stepY = (bmp.height / 24).coerceAtLeast(1)
    for (y in 0 until bmp.height step stepY) for (x in 0 until bmp.width step stepX) {
        val p = bmp.getPixel(x, y)
        AColor.colorToHSV(p, hsv)
        val sat = hsv[1]; val v = hsv[2]
        if (v < 0.15f || sat < 0.2f || (v > 0.95f && sat < 0.3f)) continue
        val weight = (sat * v).toDouble()
        r += AColor.red(p) * weight; g += AColor.green(p) * weight; b += AColor.blue(p) * weight; w += weight
    }
    if (w < 1.0) return null
    return Color((r / w).toInt(), (g / w).toInt(), (b / w).toInt())
}
