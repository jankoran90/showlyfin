package com.github.jankoran90.showlyfin.core.domain

enum class AgeRating(val displayName: String, val maxParentalRatingThreshold: Int) {
    UNRESTRICTED("Bez omezení", Int.MAX_VALUE),
    CHILDREN("Do 6 let", 6),
    FAMILY("Do 13 let", 13),
    TEEN("Do 16 let", 16),
    ADULT("18+", 18);

    companion object {
        fun fromJellyfinMaxParentalRating(rating: Int?): AgeRating = when {
            rating == null -> UNRESTRICTED
            rating <= 6 -> CHILDREN
            rating <= 13 -> FAMILY
            rating <= 16 -> TEEN
            else -> ADULT
        }
    }
}
