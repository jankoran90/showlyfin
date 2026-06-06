package com.github.jankoran90.showlyfin.debug

import android.app.Activity
import android.content.Context
import androidx.core.view.drawToBitmap
import com.github.jankoran90.showlyfin.BuildConfig
import kotlinx.coroutines.Dispatchers
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

object DebugCaptureManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

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

    private suspend fun captureScreenshot(activity: Activity, timestamp: String): File = withContext(Dispatchers.Main) {
        val bitmap = activity.window.decorView.drawToBitmap()
        val dir = File(activity.cacheDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "screenshot-$timestamp.png")
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        file
    }

    private fun dumpLog(context: Context, timestamp: String): File {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "log-$timestamp.txt")
        file.writeText(BufferTree.INSTANCE.formatLog())
        return file
    }

    private fun upload(timestamp: String, screenshot: File?, log: File?): Boolean {
        return runCatching {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            builder.addFormDataPart("timestamp", timestamp)
            builder.addFormDataPart("version", BuildConfig.VERSION_NAME)
            builder.addFormDataPart("version_code", BuildConfig.VERSION_CODE.toString())
            val metadataJson = buildString {
                append('{')
                append("\"timestamp\":\"").append(timestamp).append("\",")
                append("\"version\":\"").append(BuildConfig.VERSION_NAME).append("\",")
                append("\"versionCode\":").append(BuildConfig.VERSION_CODE)
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
                .header("User-Agent", "Showlyfin/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.onFailure { Timber.w(it, "upload failed") }.getOrDefault(false)
    }
}
