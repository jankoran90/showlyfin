package com.github.jankoran90.showlyfin.data.maestro

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plan MAESTRO — vysokoúrovňové ovládání Pioneer/Onkyo AV receiveru přes [EiscpClient].
 *
 * Skutečná master hlasitost obýváku = AVR (Xiaomi box přes HDMI signál jen digitálně zeslabuje),
 * proto Ovladač směruje hlasitost sem. Magické hodnoty ŽIVĚ OVĚŘENY na VSX-935 (#48):
 * `MVL<hex>` absolutní hlasitost · `AMT00/01` mute · `PWR01/00` power · `SLI<code>` vstup
 * (`SLI11`=STRM BOX/Xiaomi, `SLI01`=CBL/SAT hudba). Dotazy `…QSTN`.
 */
@Singleton
class AvrController @Inject constructor(
    private val eiscp: EiscpClient,
) {

    /** Stav AVR z dotazu. [reachable]=false → receiver neodpověděl/nedostupný (fallback na JF). */
    data class AvrStatus(
        val reachable: Boolean,
        val volume: Int? = null,
        val muted: Boolean = false,
    )

    /** Načte hlasitost (`MVLQSTN`) + mute (`AMTQSTN`). */
    suspend fun status(host: String): AvrStatus {
        val mvl = eiscp.command(host, "MVLQSTN", expectReply = true)
        if (mvl == null) return AvrStatus(reachable = false)
        val amt = eiscp.command(host, "AMTQSTN", expectReply = true)
        val volume = mvl.firstNotNullOfOrNull(::parseMvl)
        val muted = amt?.any { it.startsWith("AMT") && it.removePrefix("AMT").trim().startsWith("01") } ?: false
        Timber.i("[MAESTRO] AVR @%s status vol=%s muted=%b", host, volume, muted)
        return AvrStatus(reachable = true, volume = volume, muted = muted)
    }

    /** Nastaví absolutní hlasitost (`MVL<hex>`). */
    suspend fun setVolume(host: String, value: Int): Boolean {
        val hex = value.coerceIn(0, MAX_VOLUME).toString(16).uppercase().padStart(2, '0')
        return eiscp.command(host, "MVL$hex") != null
    }

    suspend fun setMute(host: String, mute: Boolean): Boolean =
        eiscp.command(host, if (mute) "AMT01" else "AMT00") != null

    suspend fun powerOn(host: String): Boolean = eiscp.command(host, "PWR01") != null

    suspend fun setInput(host: String, sliCode: String): Boolean =
        eiscp.command(host, "SLI$sliCode") != null

    private fun parseMvl(reply: String): Int? {
        val hex = reply.takeIf { it.startsWith("MVL") }?.removePrefix("MVL")?.take(2) ?: return null
        return hex.toIntOrNull(16)
    }

    companion object {
        /** Horní mez hlasitosti pro UI (Pioneer absolutní MVL; přesný strop VSX-935 doladit dle zařízení). */
        const val MAX_VOLUME = 130
        const val PORT = 60128
        // Vstupy AVR (NRI): STRM BOX = Xiaomi (sledování), CBL/SAT = hudba (nebudí TV).
        const val INPUT_STREAM_BOX = "11"
        const val INPUT_SAT_MUSIC = "01"
    }
}
