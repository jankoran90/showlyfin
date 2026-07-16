package com.github.jankoran90.showlyfin.core.appservices.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import timber.log.Timber
import java.io.File

/**
 * Plan EVERGREEN (SHW-64) — tichá instalace APK přes [PackageInstaller]. U self-updatu se STEJNÝM
 * podpisem (náš keystore) a uděleným „instalovat neznámé aplikace" projde na Androidu 12+ bez
 * systémového dialogu díky `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`. Když systém přesto
 * vyžádá akci (typicky první instalace, než se appka stane „installerem of record"), receiver
 * dostane [PackageInstaller.STATUS_PENDING_USER_ACTION] a spustí potvrzovací dialog = graceful
 * fallback. Stará cesta `ACTION_VIEW` zůstává jako záloha v [UpdateChecker.buildInstallIntent].
 */
object ApkInstaller {
    const val ACTION_INSTALL_STATUS = "com.github.jankoran90.showlyfin.INSTALL_STATUS"

    /** @return true když se session podařilo commitnout (samotná instalace doběhne asynchronně). */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("showlyfin.apk", 0, apk.length()).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            // FLAG_MUTABLE — systém do PendingIntentu dolije stav instalace (EXTRA_STATUS apod.).
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
            session.commit(pi.intentSender)
        }
        true
    }.onFailure { Timber.w(it, "silent install failed") }.getOrDefault(false)
}
