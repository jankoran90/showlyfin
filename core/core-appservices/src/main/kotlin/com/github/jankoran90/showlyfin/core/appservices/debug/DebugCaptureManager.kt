package com.github.jankoran90.showlyfin.core.appservices.debug

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import androidx.core.view.drawToBitmap
import com.github.jankoran90.showlyfin.core.appservices.AppServices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val UPLOAD_URL = "https://voice.jankoran.cz/debug/showlyfin"
private const val LIVE_URL = "https://voice.jankoran.cz/debug/showlyfin/live"

object DebugCaptureManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Živé logování — periodicky posílá log buffer na server (toggle v Nastavení) ──
    private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var liveJob: Job? = null

    fun setLiveLogging(versionInfo: String, enabled: Boolean) {
        if (enabled) {
            if (liveJob?.isActive == true) return
            liveJob = liveScope.launch {
                while (isActive) {
                    runCatching { postLive(versionInfo) }.onFailure { Timber.w(it, "live log post failed") }
                    delay(8000)
                }
            }
            Timber.i("[live-log] zapnuto")
        } else {
            liveJob?.cancel()
            liveJob = null
            Timber.i("[live-log] vypnuto")
        }
    }

    private fun postLive(versionInfo: String) {
        val text = versionInfo + "\n" + BufferTree.INSTANCE.formatLog()
        val body = text.toRequestBody("text/plain; charset=utf-8".toMediaType())
        val req = Request.Builder().url(LIVE_URL).post(body).build()
        client.newCall(req).execute().use { /* ignore body */ }
    }

    suspend fun captureAndUpload(activity: Activity): Boolean = withContext(Dispatchers.IO) {
        val ctx = activity.applicationContext
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val screenshotFile = runCatching { captureScreenshot(activity, timestamp) }
            .onFailure { Timber.w(it, "captureScreenshot failed") }
            .getOrNull()
        val logFile = runCatching { dumpLog(ctx, timestamp) }
            .onFailure { Timber.w(it, "dumpLog failed") }
            .getOrNull()
        if (screenshotFile == null && logFile == null) return@withContext false
        upload(timestamp, screenshotFile, logFile)
    }

    private suspend fun captureScreenshot(activity: Activity, timestamp: String): File {
        val bitmap = captureViaPixelCopy(activity) ?: captureViaDrawToBitmap(activity)
        val dir = File(activity.cacheDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "screenshot-$timestamp.png")
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return file
    }

    private suspend fun captureViaPixelCopy(activity: Activity): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val window = activity.window
        val view = window.decorView
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val thread = HandlerThread("pixel-copy-debug").apply { start() }
        val handler = Handler(thread.looper)
        val result = CompletableDeferred<Bitmap?>()
        try {
            PixelCopy.request(window, bitmap, { code ->
                if (code == PixelCopy.SUCCESS) result.complete(bitmap) else result.complete(null)
            }, handler)
            return result.await()
        } catch (t: Throwable) {
            return null
        } finally {
            thread.quitSafely()
        }
    }

    private suspend fun captureViaDrawToBitmap(activity: Activity): Bitmap = withContext(Dispatchers.Main) {
        runCatching { activity.window.decorView.drawToBitmap() }.getOrElse {
            val w = activity.window.decorView.width.coerceAtLeast(1)
            val h = activity.window.decorView.height.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).also { activity.window.decorView.draw(it) }
            bmp
        }
    }

    private fun dumpLog(context: Context, timestamp: String): File {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "log-$timestamp.txt")
        val crashFile = File(context.filesDir, "last_crash.txt")
        val crashText = if (crashFile.exists()) {
            "===== POSLEDNÍ ULOŽENÝ PÁD (last_crash.txt) =====\n" +
                runCatching { crashFile.readText() }.getOrDefault("(nečitelné)") +
                "\n===== KONEC PÁDU =====\n\n"
        } else {
            ""
        }
        file.writeText(crashText + BufferTree.INSTANCE.formatLog())
        return file
    }

    private fun upload(timestamp: String, screenshot: File?, log: File?): Boolean {
        return runCatching {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            val cfg = AppServices.config
            builder.addFormDataPart("timestamp", timestamp)
            builder.addFormDataPart("version", cfg.versionName)
            builder.addFormDataPart("version_code", cfg.versionCode.toString())
            val metadataJson = buildString {
                append('{')
                append("\"timestamp\":\"").append(timestamp).append("\",")
                append("\"version\":\"").append(cfg.versionName).append("\",")
                append("\"versionCode\":").append(cfg.versionCode)
                append('}')
            }
            builder.addFormDataPart(
                "metadata",
                "metadata.json",
                metadataJson.toRequestBody("application/json".toMediaType()),
            )
            screenshot?.let {
                builder.addFormDataPart(
                    "screenshot",
                    it.name,
                    it.asRequestBody("image/png".toMediaType()),
                )
            }
            log?.let {
                builder.addFormDataPart(
                    "log",
                    it.name,
                    it.asRequestBody("text/plain".toMediaType()),
                )
            }
            val request = Request.Builder()
                .url(UPLOAD_URL)
                .post(builder.build())
                .header("User-Agent", cfg.userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.onFailure { Timber.w(it, "upload failed") }.getOrDefault(false)
    }
}
