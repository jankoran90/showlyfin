package com.github.jankoran90.showlyfin.core.domain.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import timber.log.Timber

/**
 * Plan EVEN — hlasitostní vyrovnání / DRC normalizér napojený na **audio session id** ExoPlayeru.
 *
 * Sdílený pro poslech (audioknihy/podcasty) i film. Vyrovná tiché a hlasité pasáže:
 *  - **API 28+:** [DynamicsProcessing] = 1-pásmový kompresor + limiter + input/makeup gain (reálné DRC).
 *  - **API 23–27:** [LoudnessEnhancer] = pevný boost (jen profil [Profile.LISTEN]; pro film by boost
 *    tlačil špatným směrem → film bez DRC na starých zařízeních nedělá nic).
 *
 * Dva profily se liší laděním:
 *  - [Profile.LISTEN] — řeč potichu nahraná → boost + komprese (zvedne tiché epizody/kapitoly).
 *  - [Profile.MOVIE] — krotí špičky (přestřelky na noc) a jemně zvedá dialogy, **bez velkého
 *    celkového boostu** (film bývá hlasitý sám o sobě). Jen telefon; na TV hraje box (passthrough do
 *    AVR se efekt nedotkne).
 *
 * Jedna instance na životnost session id; [apply] umí přepnout úroveň za běhu. Nezapomeň [release].
 */
class AudioBoost(private val audioSessionId: Int) {

    enum class Profile { LISTEN, MOVIE }

    private var dynamics: DynamicsProcessing? = null
    private var enhancer: LoudnessEnhancer? = null
    private var currentLevel = -1
    private var currentProfile: Profile? = null

    private data class Params(
        val inputGainDb: Float,
        val thresholdDb: Float,
        val ratio: Float,
        val postGainDb: Float,
        val attackMs: Float,
        val releaseMs: Float,
        val limiterThresholdDb: Float,
    )

    /** Nastaví / přepne úroveň. 0 = vypnout efekt. Idempotentní pro stejnou úroveň + profil. */
    fun apply(level: Int, profile: Profile) {
        if (level == currentLevel && profile == currentProfile) return
        currentLevel = level
        currentProfile = profile
        release()
        if (level <= 0 || audioSessionId == 0) return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> applyDynamics(level, profile)
                profile == Profile.LISTEN -> applyEnhancer(level)
                else -> Unit // MOVIE na API<28: DRC nedostupné, booster nechceme.
            }
        }.onFailure {
            Timber.w(it, "[EVEN] nelze připojit audio efekt (level=%d profile=%s)", level, profile)
            release()
        }
    }

    fun release() {
        runCatching { dynamics?.release() }
        runCatching { enhancer?.release() }
        dynamics = null
        enhancer = null
    }

    private fun applyDynamics(level: Int, profile: Profile) {
        val p = paramsFor(level, profile)
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ false, /* preEqBandCount = */ 0,
            /* mbcInUse = */ true, /* mbcBandCount = */ 1,
            /* postEqInUse = */ false, /* postEqBandCount = */ 0,
            /* limiterInUse = */ true,
        ).build()
        val dp = DynamicsProcessing(/* priority = */ 0, audioSessionId, config)
        // channelCount může framework upravit oproti configu → iteruj reálný počet.
        for (ch in 0 until dp.channelCount) {
            val mbc = dp.getMbcByChannelIndex(ch)
            mbc.getBand(0).apply {
                setEnabled(true)
                setAttackTime(p.attackMs)
                setReleaseTime(p.releaseMs)
                setRatio(p.ratio)
                setThreshold(p.thresholdDb)
                setKneeWidth(6f)
                setPreGain(0f)
                setPostGain(p.postGainDb)
            }.also { mbc.setBand(0, it) }
            dp.setMbcByChannelIndex(ch, mbc)

            val limiter = dp.getLimiterByChannelIndex(ch)
            limiter.setEnabled(true)
            limiter.setAttackTime(1f)
            limiter.setReleaseTime(60f)
            limiter.setRatio(10f)
            limiter.setThreshold(p.limiterThresholdDb)
            limiter.setPostGain(0f)
            dp.setLimiterByChannelIndex(ch, limiter)
        }
        dp.setInputGainAllChannelsTo(p.inputGainDb)
        dp.setEnabled(true)
        dynamics = dp
        Timber.i("[EVEN] DynamicsProcessing level=%d profile=%s ch=%d", level, profile, dp.channelCount)
    }

    private fun applyEnhancer(level: Int) {
        val gainDb = when (level) {
            1 -> 3
            2 -> 6
            3 -> 9
            else -> 8
        }
        enhancer = LoudnessEnhancer(audioSessionId).apply {
            setTargetGain(gainDb * 100) // mB
            setEnabled(true)
        }
        Timber.i("[EVEN] LoudnessEnhancer level=%d gain=%ddB", level, gainDb)
    }

    private fun paramsFor(level: Int, profile: Profile): Params = when (profile) {
        Profile.LISTEN -> when (level) {
            // Mírná → Střední (def.) → Silná → Noční. Boost + komprese (řeč potichu nahraná).
            1 -> Params(2f, -20f, 2f, 1f, 15f, 250f, -1f)
            2 -> Params(4f, -24f, 3f, 2f, 12f, 220f, -1f)
            3 -> Params(6f, -28f, 4f, 3f, 10f, 200f, -1f)
            else -> Params(5f, -30f, 6f, 2f, 5f, 150f, -2f)
        }
        Profile.MOVIE -> when (level) {
            // Mírná → Střední → Noční. Krotí špičky + jemně zvedá dialogy, bez velkého boostu.
            1 -> Params(0f, -26f, 2.5f, 0f, 15f, 250f, -1.5f)
            2 -> Params(1f, -28f, 4f, 1f, 12f, 200f, -1.5f)
            else -> Params(2f, -30f, 8f, 1f, 5f, 150f, -2f)
        }
    }

    companion object {
        /** Klíč v `trakt_prefs` pro úroveň DRC filmu (0 Vyp default / 1 Mírná / 2 Střední / 3 Noční). */
        const val MOVIE_DRC_KEY = "movie_drc_level"
    }
}
