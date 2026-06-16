package com.github.jankoran90.showlyfin.core.domain

/**
 * Plan EVERGREEN (SHW-64) — pojistka proti tomu, aby tichá auto-instalace nové verze na pozadí
 * neutla běžící přehrávání. Playback služby ([playbackActive]) ji přepínají podle stavu přehrávače;
 * `UpdateCheckWorker` tichou instalaci odloží, dokud něco hraje (i se zhasnutou obrazovkou).
 */
object InstallGuard {
    @Volatile
    var playbackActive: Boolean = false
}
