package com.github.jankoran90.showlyfin.feature.listen.ui

/**
 * AMPHORA F7 (Z2, tester 1.0.107/108: „Selhalo“ ukazovalo anglický text výjimky typu
 * `Unable to resolve host …`) — chyba stažení česky. Neznámá chyba: dospělý vidí původní text
 * (pomáhá ladit), dítě jen obecnou větu.
 */
internal object DownloadErrorText {
    fun cz(raw: String?, forKids: Boolean): String {
        val r = raw?.lowercase().orEmpty()
        return when {
            r.isBlank() -> GENERIC
            "resolve host" in r || "unknownhost" in r || "no address associated" in r || "network is unreachable" in r ->
                "Není připojení k internetu"
            "timeout" in r || "timed out" in r -> "Server neodpovídal včas"
            "no space" in r || "enospc" in r -> "V telefonu není dost místa"
            "connection reset" in r || "connection refused" in r || "connection abort" in r ||
                "unexpected end of stream" in r || "socket" in r -> "Spojení se přerušilo"
            "404" in r || "not found" in r -> "Soubor na serveru už není"
            "401" in r || "403" in r || "unauthorized" in r || "forbidden" in r -> "Server stažení nepovolil"
            SERVER_ERROR.containsMatchIn(r) -> "Chyba na serveru"
            forKids -> GENERIC
            else -> raw!!
        }
    }

    private const val GENERIC = "Stažení se nepovedlo"
    private val SERVER_ERROR = Regex("(http|code|status|error)[^0-9]{0,3}5\\d\\d\\b")
}
