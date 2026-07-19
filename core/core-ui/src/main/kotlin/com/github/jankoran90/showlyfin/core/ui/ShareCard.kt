package com.github.jankoran90.showlyfin.core.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * PLAKÁT (SHW-98) — spuštění sdílení filmu jako obrázku.
 * Načte poster+fanart (JDK HttpURLConnection → BitmapFactory; TMDB přímé JPEG, žádná coil3 API závislost),
 * nechá [ShareCardRenderer] vykreslit kartu, uloží PNG do cacheDir/share a spustí `Intent.ACTION_SEND`
 * s typem image přes FileProvider (authority `${packageName}.fileprovider`, cache-path viz file_paths.xml).
 */
object ShareCard {

    /** Vyrenderuje a odešle sdílecí kartu filmu. Volat z coroutine scope (suspend). */
    suspend fun shareFilm(
        context: Context,
        data: ShareCardData,
        posterUrl: String?,
        backdropUrl: String?,
    ) {
        // Zdroje ber ve vyšším rozlišení (supersamplovaný canvas jinak upscaluje malé náhledy).
        val poster = loadBitmap(upscaleTmdb(posterUrl, "w780"))
        val backdrop = loadBitmap(upscaleTmdb(backdropUrl, "w1280"))
        val bmp = ShareCardRenderer.render(data, poster, backdrop)
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val safe = data.title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").take(40).ifBlank { "film" }
            val file = File(dir, "karta_$safe.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val caption = buildString {
            append(data.title)
            data.year?.let { append(" (").append(it).append(")") }
            data.csfdPct?.let { append(" · ČSFD ").append(it).append(" %") }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            putExtra(Intent.EXTRA_SUBJECT, data.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Sdílet kartu filmu").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }

    /** U TMDB URL nahradí velikostní token (w500/h632/original) za [size] = ostřejší zdroj pro kartu. */
    private fun upscaleTmdb(url: String?, size: String): String? =
        if (url != null && url.contains("image.tmdb.org")) {
            url.replace(Regex("/t/p/(w\\d+|h\\d+|original)/"), "/t/p/$size/")
        } else {
            url
        }

    private suspend fun loadBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
