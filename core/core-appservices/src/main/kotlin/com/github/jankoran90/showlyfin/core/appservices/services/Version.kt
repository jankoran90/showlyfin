package com.github.jankoran90.showlyfin.core.appservices.services

data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int = when {
        major != other.major -> major.compareTo(other.major)
        minor != other.minor -> minor.compareTo(other.minor)
        else -> patch.compareTo(other.patch)
    }

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val regex = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+).*$")

        fun parse(tag: String?): Version? {
            if (tag.isNullOrBlank()) return null
            val m = regex.matchEntire(tag.trim()) ?: return null
            return Version(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].toInt(),
                patch = m.groupValues[3].toInt(),
            )
        }
    }
}
