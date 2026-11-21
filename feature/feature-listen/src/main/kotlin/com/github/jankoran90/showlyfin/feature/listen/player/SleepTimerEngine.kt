package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.sqrt

/**
 * AMPHORA (SHW-124) F4 / PR1 — minutový časovač spánku přehrávače:
 *  - posledních [FADE_MS] postupně ztiší ([ListenPlayerPrefs.sleepFade]),
 *  - zatřesení telefonem vrátí časovač na plnou délku ([ListenPlayerPrefs.sleepShake]) + krátké cvrnknutí,
 *  - pamatuje místo, kde jsi naposledy prokazatelně nespal (nastavení / zatřesení), a po vypnutí ho
 *    uloží jako [ListenPlayerPrefs.WakeMark] → přehrávač nabídne „Usnul jsi asi v…, vrátit?“.
 * Ovládá přehrávač jen přes [Host] (žije v [AudiobookPlayerConnection], který je na hraně velikosti).
 */
internal class SleepTimerEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: ListenPlayerPrefs,
    private val host: Host,
) {
    interface Host {
        fun onMinutesLeft(minutes: Int?)
        fun pausePlayback()
        fun setVolume(volume: Float)
        /** Klíč právě hrané položky a pozice v ní (ms), null = nic nehraje. */
        fun currentPosition(): Pair<String, Long>?
    }

    private var job: Job? = null
    private var lengthMs = 0L
    private var endAt = 0L
    private var awake: ListenPlayerPrefs.WakeMark? = null
    private var sensorOn = false
    private var lastShake = 0L

    val isRunning: Boolean get() = job?.isActive == true

    fun start(minutes: Int) {
        cancel()
        lengthMs = minutes * 60_000L
        endAt = SystemClock.elapsedRealtime() + lengthMs
        markAwake()
        if (prefs.sleepShake) registerShake()
        job = scope.launch {
            var faded = false
            while (true) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                host.onMinutesLeft(((left + 59_999L) / 60_000L).toInt())
                if (prefs.sleepFade && left <= FADE_MS) {
                    host.setVolume((left.toFloat() / FADE_MS).coerceIn(0.05f, 1f))
                    faded = true
                } else if (faded) {
                    host.setVolume(1f); faded = false
                }
                delay(if (left <= FADE_MS) 500L else 1_000L)
            }
            finish()
        }
    }

    /** Zruší časovač (bez pauzy) a vrátí hlasitost. */
    fun cancel() {
        job?.cancel()
        job = null
        unregisterShake()
        host.setVolume(1f)
        host.onMinutesLeft(null)
    }

    private fun finish() {
        unregisterShake()
        host.onMinutesLeft(null)
        host.pausePlayback()
        host.setVolume(1f)
        job = null
        if (prefs.sleepWakeOffer) prefs.wakeMark = awake
        Timber.i("[PR1] spánek: pauza, poslední bdělé místo %s", awake)
    }

    private fun markAwake() {
        awake = host.currentPosition()?.let { (key, pos) -> ListenPlayerPrefs.WakeMark(key, pos, System.currentTimeMillis()) }
    }

    private fun onShake() {
        if (!isRunning) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShake < 1_500L) return
        lastShake = now
        endAt = now + lengthMs
        host.setVolume(1f)
        markAwake()
        vibrate()
        Timber.i("[PR1] spánek: zatřesení → znovu %d min", lengthMs / 60_000L)
    }

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
            val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            if (g > SHAKE_G) onShake()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun registerShake() {
        if (sensorOn) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorOn = sm.registerListener(shakeListener, acc, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterShake() {
        if (!sensorOn) return
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(shakeListener)
        sensorOn = false
    }

    private fun vibrate() = runCatching {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(60L)
    }

    private companion object {
        const val FADE_MS = 30_000L
        /** Zatřesení = zrychlení nad 2,3 g (běžné položení / otočení v posteli je pod 1,5 g). */
        const val SHAKE_G = 2.3f
    }
}
