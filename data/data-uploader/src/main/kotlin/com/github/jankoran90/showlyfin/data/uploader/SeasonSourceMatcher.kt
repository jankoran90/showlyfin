package com.github.jankoran90.showlyfin.data.uploader

import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream

/**
 * SEZONA (SHW-113) fáze 2 — „zdroj sezóny": jeden potvrzený zdroj se promítne do VŠECH dílů sezóny,
 * aby Přehrát u dalšího dílu streamovalo rovnou, bez pickeru (user 2026-08-01 16:37).
 *
 * 🔬 **Proč receptura, a ne jen otisk torrentu** — ověřeno naživo na Perníkovém tátovi (2026-08-01):
 * - **SK/CZ Torrents = pravý season pack:** stejný `infoHash` u E1/E2/E5, jen jiný `fileIdx`
 *   (0 → 1 → 4). Addon si soubor v balíku dohledá sám, takže stačí trefit tentýž otisk.
 *   (User čekal opak — že u českých zdrojů to nepůjde. Jde to tam NEJLÍP.)
 * - **AIOStreams (RD) balíky taky existují** — v popisu je vidět „📦 12.8 GB / 392 GB", tedy soubor
 *   uvnitř velkého balíku — ale addon vrací neprůhlednou proxy adresu a **`infoHash` je null**.
 *   Tam se tentýž zdroj pozná jen podle RECEPTURY: addon + release grupa + rozlišení (+ kodek).
 *
 * Proto dvě úrovně shody. Když nesedí ani jedna, vrací se null a picker se otevře normálně —
 * radši se zeptat, než přehrát cizí/horší zdroj.
 */
object SeasonSourceMatcher {

    /** Jak jistá je shoda kandidáta s recepturou sezóny. Pořadí = priorita výběru. */
    enum class Confidence { PACK, RECIPE, QUALITY }

    data class Match(val stream: UploaderStream, val confidence: Confidence)

    /**
     * Vyber pro díl zdroj odpovídající [seasonSource]. Vrací null, když nic nesedí dost dobře.
     *
     * [requirePlayable] = ber jen zdroje, které hrají HNED (cached na RD / sdilej přes proxy).
     * Necachovaný torrent by znamenal čekání na stažení, což u „Přehrát a jede" není splněný slib.
     */
    fun pick(
        seasonSource: UploaderStream,
        candidates: List<UploaderStream>,
        requirePlayable: Boolean = true,
    ): Match? {
        val usable = if (requirePlayable) candidates.filter { playsNow(it) } else candidates
        if (usable.isEmpty()) return null

        // 1) Pravý season pack — tentýž torrent. `fileIdx` je pro každý díl jiný a addon ho dodal
        //    správně, takže kandidáta bereme CELÉHO (nikdy nepřepisujeme fileIdx z receptury!).
        val hash = seasonSource.infoHash?.lowercase()?.takeIf { it.isNotBlank() }
        if (hash != null) {
            usable.firstOrNull { it.infoHash?.lowercase() == hash }
                ?.let { return Match(it, Confidence.PACK) }
        }

        // 2) Táž receptura — stejný addon, release grupa i rozlišení. Pokrývá jak balíky bez otisku
        //    (AIOStreams), tak sezóny vydané po dílech od jedné grupy (což je pro diváka totéž:
        //    stejná kvalita, stejný zvuk, stejné časování titulků).
        val group = releaseGroup(seasonSource)
        val res = seasonSource.quality.resolution?.lowercase()
        if (group != null) {
            usable.firstOrNull {
                sameAddon(it, seasonSource) &&
                    releaseGroup(it) == group &&
                    (res == null || it.quality.resolution?.lowercase() == res)
            }?.let { return Match(it, Confidence.RECIPE) }
        }

        // 3) Poslední úroveň — týž addon a totéž rozlišení. Bez grupy je to volnější, ale pořád je to
        //    vědomá volba diváka („4K z AIOStreams"), ne náhodný zdroj z vrchu seznamu.
        if (res != null) {
            usable.firstOrNull {
                sameAddon(it, seasonSource) && it.quality.resolution?.lowercase() == res
            }?.let { return Match(it, Confidence.QUALITY) }
        }
        return null
    }

    /** Hraje hned? Cached na RD nebo sdilej (jede přes náš proxy). Necachovaný torrent = ne. */
    fun playsNow(s: UploaderStream): Boolean =
        s.quality.rdReady || s.quality.rdSaved || s.url?.startsWith("sdilej://") == true

    private fun sameAddon(a: UploaderStream, b: UploaderStream): Boolean =
        (a.addon ?: "").equals(b.addon ?: "", ignoreCase = true)

    // Release grupa = ocásek za poslední pomlčkou před příponou: „…HEVC-FIGHTCLUB.mkv" → FIGHTCLUB.
    // AIOStreams ji navíc píše do popisu za 🏷️. Bereme obojí, ať máme co porovnávat i u strohých názvů.
    private val RE_GROUP_FILE = Regex("""-([A-Za-z0-9]{2,20})(?:\.[A-Za-z0-9]{2,4})?$""")
    private val RE_GROUP_TAG = Regex("""🏷️\s*([A-Za-z0-9._-]{2,30})""")

    /** Release grupa zdroje (velkými), nebo null. Slouží k porovnání, ne k zobrazení. */
    fun releaseGroup(s: UploaderStream): String? {
        val desc = s.description.orEmpty()
        RE_GROUP_TAG.find(desc)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return it.uppercase() }
        // U popisů přes víc řádků zkoušej každý zvlášť — název souboru bývá jen na jednom z nich.
        for (line in (desc.lines() + s.name.orEmpty().lines()).map { it.trim() }) {
            if (line.isEmpty()) continue
            RE_GROUP_FILE.find(line)?.groupValues?.get(1)
                ?.takeIf { it.length in 2..20 && it.any { c -> c.isLetter() } }
                ?.let { return it.uppercase() }
        }
        return null
    }
}
