package com.github.jankoran90.showlyfin.feature.detail.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * SEJF (FLM-03) — průběh ukládání do vlastní filmotéky v SYSTÉMOVÉ liště oznámení.
 * user (TG 2026-08-30 07:39): *„Po kliknutí chci vidět progress v app notifikaci systémové liště
 * oznámení."* Vzor = SpotlightCheckWorker (channel + notify). Průběžní notifikace je `ongoing`
 * (nepůjde smáznout, dokud download běží), finální oznámí výsledek a sžene se kliknutím.
 */
object SejfNotifier {
    private const val CHANNEL_ID = "filmy_sejf"
    private const val CHANNEL_NAME = "Vlastní filmotéka"
    private const val NOTIF_ID = 4711

    private fun manager(ctx: Context): NotificationManager? {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            )
        }
        // vypnuté notifikace appky / zrušená oprávnění → nechceme crashnout uložení filmu
        return nm.takeIf { it.areNotificationsEnabled() }
    }

    /** Průběh (aktualizuje tutéž notifikaci, ongoing). */
    fun progress(ctx: Context, text: String) {
        val nm = manager(ctx) ?: return
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Ukládám do vlastní filmotéky")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    /** Výsledek (done/error) — kliknutelné zmizí, není ongoing. */
    fun result(ctx: Context, success: Boolean, text: String) {
        val nm = manager(ctx) ?: return
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (success) "Uloženo na dellhome" else "Uložení selhalo")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    /** Sženout notifikaci (např. při resetu stavu). */
    fun cancel(ctx: Context) {
        runCatching { manager(ctx)?.cancel(NOTIF_ID) }
    }
}
