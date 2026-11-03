package com.github.jankoran90.showlyfin.feature.listen.audioteka

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.uploader.AudiobookOwnershipRepository
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBook
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtRelated
import javax.inject.Inject

/**
 * Server značí „V knihovně" přes VŠECHNY ABS knihovny a o profilech neví. Tady se značka nechá jen u knih,
 * které aktivní profil smí vidět — stejné pravidlo jako Poslech: vlastní, sdílené, legacy a knihy
 * z dětských knihoven (revize 2026-11-03: katalog jinak otevřel cizí soukromou knihu).
 */
class AudiotekaOwnedFilter @Inject constructor(
    private val ownership: AudiobookOwnershipRepository,
    private val profiles: ProfileRepository,
) {
    suspend fun apply(items: List<AtBook>, refresh: Boolean = true): List<AtBook> {
        if (items.none { it.inLibrary != null }) return items
        val active = profiles.activeProfile.value ?: return items
        val cfg = profiles.activeConfig.value
        val kidsIds = profiles.getAll().filterNot { it.isAdmin }
            .flatMap { ProfileConfig.fromJson(it.configJson).absLibraryWhitelist.orEmpty() }
            .toSet()
        if (refresh) ownership.refresh()
        return items.map { b ->
            val id = b.inLibrary ?: return@map b
            val visible = if (!active.isAdmin) b.inLibraryLib in cfg.absLibraryWhitelist.orEmpty()
            else b.inLibraryLib in kidsIds || ownership.isVisible(id, active.profileUuid, cfg.sharedAudiobookIds)
            if (visible) b else b.copy(inLibrary = null, inLibraryLib = null)
        }
    }

    suspend fun apply(r: AtRelated): AtRelated {
        ownership.refresh()
        return r.copy(
            detail = r.detail?.let { d -> d.copy(book = apply(listOf(d.book), refresh = false).first()) },
            sections = r.sections.map { it.copy(items = apply(it.items, refresh = false)) },
        )
    }
}
