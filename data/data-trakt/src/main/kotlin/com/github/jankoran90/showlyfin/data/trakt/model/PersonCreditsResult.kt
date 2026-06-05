package com.github.jankoran90.showlyfin.data.trakt.model

data class PersonCreditsResult(
    val cast: List<PersonCredit>?,
    val crew: Map<String, List<PersonCredit>>?,
)
