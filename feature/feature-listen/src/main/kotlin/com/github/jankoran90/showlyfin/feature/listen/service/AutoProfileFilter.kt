package com.github.jankoran90.showlyfin.feature.listen.service

import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.Podcast
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource

// Profilové filtry Android Auto (Codex sweep 2026-10-12): browse strom dřív ukazoval VŠE — děti
// dospělou knihovnu, Nel Honzovy nesdílené zdroje/knihy. Stejná pravidla jako telefonní UI
// (ListenViewModel / HomeViewModel / ListenScreen), jen nad aktivním profilem služby.

private val AudiobookPlayerService.isKidsProfile: Boolean
    get() = profileRepository.activeProfile.value?.isAdmin == false

/** Zdroje (RSS/YT/ČT), které aktivní profil vidí — [PodcastSource.isVisibleTo]. */
internal fun AudiobookPlayerService.visibleSources(): List<PodcastSource> {
    val cfg = profileRepository.activeConfig.value
    val uuid = profileRepository.activeProfile.value?.profileUuid
    return sourcesRepo.sources.value.filter {
        it.isVisibleTo(uuid, isKidsProfile, cfg.sharedSourceKeys, cfg.visibleForKidsSourceKeys)
    }
}

/** ABS knihovna podle whitelistu profilu (null = bez omezení). */
internal fun AudiobookPlayerService.isLibraryVisible(libraryId: String): Boolean =
    profileRepository.activeConfig.value.absLibraryWhitelist?.contains(libraryId) ?: true

internal fun AudiobookPlayerService.visibleLibraries(libs: List<AbsLibrary>): List<AbsLibrary> =
    libs.filter { isLibraryVisible(it.id) }

/** Audioknihy: dítě jen z whitelistu knihoven, dospělý svoje + legacy + sdílené. */
internal fun AudiobookPlayerService.visibleBooks(books: List<Audiobook>): List<Audiobook> {
    val active = profileRepository.activeProfile.value ?: return books
    val cfg = profileRepository.activeConfig.value
    if (!active.isAdmin) {
        val wl = cfg.absLibraryWhitelist ?: return emptyList()
        return books.filter { it.libraryId in wl }
    }
    return books.filter { audiobookOwnership.isVisible(it.id, active.profileUuid, cfg.sharedAudiobookIds) }
}

/** ABS podcasty: dítě jen schválené (`abs:id`), dospělý bez skrytých. */
internal fun AudiobookPlayerService.visiblePodcasts(podcasts: List<Podcast>): List<Podcast> {
    val cfg = profileRepository.activeConfig.value
    return if (isKidsProfile) podcasts.filter { cfg.isAbsPodcastVisibleForKids(it.id) }
    else podcasts.filter { it.id !in cfg.hiddenPodcastIds }
}
