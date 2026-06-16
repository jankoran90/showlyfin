package com.github.jankoran90.showlyfin.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import timber.log.Timber

/**
 * Plan EVERGREEN (SHW-64) — výsledek tiché instalace z [ApkInstaller]. Při
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] (systém přesto chce potvrzení — typicky první
 * self-update) spustí systémový potvrzovací dialog = graceful fallback. Po úspěchu vyčistí uloženou
 * nabídku, ať se update-popup po aktualizaci neukáže znovu (jako v Plan ENCORE).
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }
                        .onFailure { e -> Timber.w(e, "install confirm intent failed") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Timber.i("Showlyfin tiše aktualizován")
                UpdatePreferences.clearAvailable(context)
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.w("install failed status=$status msg=$msg")
            }
        }
    }
}
