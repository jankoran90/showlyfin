package com.github.jankoran90.showlyfin.core.db.di

import com.github.jankoran90.showlyfin.core.db.repository.CtvWatchedStoreImpl
import com.github.jankoran90.showlyfin.core.db.repository.VideoResumeStoreImpl
import com.github.jankoran90.showlyfin.core.domain.resume.CtvWatchedStore
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * VLTAVA (SHW-110) — vazba rozhraní z `core-domain` na implementaci nad SUBSTRATE Room.
 *
 * Proč rozhraní: `feature-playback` (hlásí dokoukání) `core-db` NEVIDÍ, ale stav musí být per profil
 * a cross-device, tedy v Room. Rozhraní žije v `core-domain` (vidí ho všichni), implementace tady.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreDbBindingsModule {

    @Binds
    @Singleton
    abstract fun bindsCtvWatchedStore(impl: CtvWatchedStoreImpl): CtvWatchedStore

    /** Pozice videa: dřív lokální prefs bez profilu → nově Room `playback_state` per profil + cross-device. */
    @Binds
    @Singleton
    abstract fun bindsVideoResumeStore(impl: VideoResumeStoreImpl): VideoResumeStore
}
