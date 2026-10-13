package com.github.jankoran90.showlyfin.data.uploader.subtitle

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kopie AI překladů titulků V ZAŘÍZENÍ (AMFITEÁTR FLM-05 F2, 2026-10-13). User: „data musí být uložena
 * buď v zařízení nebo dellhome, ideálně jako dvojí, takže je lze pak vždy znovupoužít nebo navázat".
 * Server (dellhome) drží originál; každé úspěšné stažení `ai_*` titulku se uloží i sem a když server
 * není dostupný (offline, výpadek, restart), přehrávač dostane tuhle kopii. Jen `filesDir` appky —
 * přežije restart i vyčištění cache, smaže ho až odinstalace.
 */
@Singleton
class AiSubtitleLocalStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, "ai_titulky").apply { mkdirs() }

    private fun fileOf(subId: String): File? =
        subId.takeIf { it.startsWith("ai_") && SAFE.matches(it) }?.let { File(dir, "$it.srt") }

    suspend fun save(subId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val f = fileOf(subId) ?: return@withContext
        if (bytes.isEmpty()) return@withContext
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) { f.writeBytes(bytes); tmp.delete() }
        }.onFailure { Timber.w(it, "[Lingua] lokální kopii titulků %s se nepodařilo uložit", subId) }
    }

    suspend fun read(subId: String): ByteArray? = withContext(Dispatchers.IO) {
        fileOf(subId)?.takeIf { it.exists() && it.length() > 0 }?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    private companion object {
        val SAFE = Regex("""^[A-Za-z0-9_.-]{4,128}$""")
    }
}
