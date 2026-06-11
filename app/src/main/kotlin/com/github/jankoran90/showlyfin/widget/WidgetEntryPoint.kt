package com.github.jankoran90.showlyfin.widget

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * Glance widgety a jejich ActionCallbacky běží mimo Compose/VM lifecycle, takže si Hilt
 * závislosti vytáhnou přes tento EntryPoint (ne přes @Inject konstruktor).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun naTvService(): NaTvService

    @Named("traktPreferences")
    fun traktPreferences(): SharedPreferences
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
