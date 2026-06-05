package com.github.jankoran90.showlyfin.data.trakt.model

data class SeasonTranslation(
    val season: Int,
    val number: Int,
    val ids: Ids,
    val translations: List<Translation>?,
)
