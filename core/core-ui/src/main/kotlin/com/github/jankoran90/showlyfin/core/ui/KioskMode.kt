package com.github.jankoran90.showlyfin.core.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Dětský telefon / kiosek (TREEHOUSE INF-20, 2026-11-19): Dětský launcher drží telefon v Lock Task
 * režimu a pouští jen launcher + Slovo + audioman. Cokoli, co otevírá jinou appku (sdílení, systémový
 * instalátor aktualizace), v kiosku nic neudělá a dítě by nechápalo → skrýt. Aktualizace Slova instaluje
 * launcher sám z OTA manifestu.
 *
 * Režim: AUTO = podle Lock Task stavu systému (výchozí), ON/OFF = ručně v Nastavení.
 */
object KioskMode {
    enum class Mode(val key: String, val label: String) {
        AUTO("auto", "Poznat samo"),
        ON("on", "Zapnuto"),
        OFF("off", "Vypnuto"),
    }

    private const val PREFS = "kiosk_prefs"
    private const val KEY_MODE = "kiosk_mode"

    fun mode(context: Context): Mode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return Mode.entries.firstOrNull { it.key == raw } ?: Mode.AUTO
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.key).apply()
    }

    /** Běží telefon právě v zamčeném režimu (Lock Task / připnutí obrazovky)? */
    fun isLockTaskActive(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }.getOrDefault(false)

    fun isActive(context: Context): Boolean = when (mode(context)) {
        Mode.ON -> true
        Mode.OFF -> false
        Mode.AUTO -> isLockTaskActive(context)
    }
}

/** Kiosek aktivní — přepočítá se při každém návratu obrazovky do popředí. */
@Composable
fun rememberKioskActive(): Boolean {
    val context = LocalContext.current
    var active by remember { mutableStateOf(KioskMode.isActive(context)) }
    LifecycleResumeEffect(Unit) {
        active = KioskMode.isActive(context)
        onPauseOrDispose { }
    }
    return active
}
