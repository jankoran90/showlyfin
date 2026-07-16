package com.github.jankoran90.showlyfin.core.appservices.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.jankoran90.showlyfin.core.appservices.AppServices
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "trakt_prefs"
private const val KEY_BASE_URL = "uploader_base_url"
private const val APK_DIR_NAME = "updates"

/**
 * Self-hosted auto-update (Plan CHANNEL) — žádný GitHub. Manifest `GET <base><updateManifestPath>`
 * nese poslední `versionCode/versionName/notes`; porovnání přes [AppServices] `config.versionCode`;
 * APK z `<base><updateApkPath>`. Endpointy jsou PUBLIC (bez tokenu). Base = uploader prefs
 * (`uploader_base_url`, ukládá GATEKEY) s fallbackem na `config.baseUrl`.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)?.trim()?.takeIf { it.isNotBlank() }
        return (stored ?: AppServices.config.baseUrl).trimEnd('/')
    }

    suspend fun fetchManifest(context: Context): ReleaseManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl(context)}${AppServices.config.updateManifestPath}")
                .header("User-Agent", AppServices.config.userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                gson.fromJson(body, ReleaseManifest::class.java)
            }
        }.onFailure { Timber.w(it, "fetchManifest failed") }.getOrNull()
    }

    fun isUpdateAvailable(manifest: ReleaseManifest?): Boolean {
        if (manifest == null) return false
        return manifest.versionCode > AppServices.config.versionCode
    }

    suspend fun downloadApk(
        context: Context,
        manifest: ReleaseManifest,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl(context)}${AppServices.config.updateApkPath}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val total = response.body?.contentLength()?.takeIf { it > 0 }
                    ?: manifest.size.takeIf { it > 0 } ?: 0L
                val dir = File(context.filesDir, APK_DIR_NAME).apply { mkdirs() }
                val outFile = File(dir, "${AppServices.config.appKey}-${manifest.versionCode}.apk")
                if (outFile.exists()) outFile.delete()
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var loaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            loaded += read
                            if (total > 0) onProgress((loaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                outFile
            }
        }.onFailure { Timber.w(it, "downloadApk failed") }.getOrNull()
    }

    fun buildInstallIntent(context: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
