package com.github.jankoran90.showlyfin.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.jankoran90.showlyfin.BuildConfig
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

private const val RELEASE_URL = "https://api.github.com/repos/jankoran90/showlyfin/releases/latest"
private const val APK_DIR_NAME = "updates"

@Singleton
class UpdateChecker @Inject constructor() {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Showlyfin/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                gson.fromJson(body, GitHubRelease::class.java)
            }
        }.onFailure { Timber.w(it, "fetchLatestRelease failed") }.getOrNull()
    }

    fun isUpdateAvailable(release: GitHubRelease?): Boolean {
        if (release == null || release.draft || release.prerelease) return false
        val latest = Version.parse(release.tagName) ?: return false
        val current = Version.parse(BuildConfig.VERSION_NAME) ?: return false
        return latest > current
    }

    fun findApkAsset(release: GitHubRelease): GitHubReleaseAsset? =
        release.assets.firstOrNull { (it.name?.endsWith(".apk", ignoreCase = true) == true) && !it.browserDownloadUrl.isNullOrBlank() }

    suspend fun downloadApk(
        context: Context,
        asset: GitHubReleaseAsset,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val url = asset.browserDownloadUrl ?: return@withContext null
        runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val total = response.body?.contentLength()?.takeIf { it > 0 } ?: asset.size.takeIf { it > 0 } ?: 0L
                val dir = File(context.filesDir, APK_DIR_NAME).apply { mkdirs() }
                val outFile = File(dir, "showlyfin-${asset.name ?: "latest"}.apk")
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
