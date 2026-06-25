package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import java.text.Normalizer

/**
 * TWINE (SHW-74 / plán F7): párování AUDIO (RSS) ↔ VIDEO (YouTube) epizod téhož pořadu a auto-návrh
 * propojení zdrojů. Vše čistě heuristika nad názvy + daty — TVRDÉ prahy, ať se omylem nespojí cizí
 * epizody (raději nespárováno než špatně spárováno). Žádné síťové volání, žádný stav.
 */
object PodcastPairing {

    /** Sloučená epizoda: audio (přehrát zvuk) a/nebo video (přehrát video). Aspoň jedno != null. */
    data class MergedEpisode(
        val title: String,
        val date: String?,
        val imageUrl: String?,
        val description: String?,
        val durationSec: Double,
        /** Verze k přehrání jako ZVUK (RSS enclosure preferováno; jinak YT audio proxy). */
        val audio: SourceEpisode?,
        /** YT verze k přehrání jako VIDEO (null = jen audio). */
        val video: SourceEpisode?,
    ) {
        /** Klíč řádku (resume/zvýraznění) — preferuj audio, jinak video. */
        val key: String get() = (audio ?: video)?.resumeKey ?: (audio ?: video)?.id ?: title
    }

    /**
     * Normalizuje název epizody pro porovnání: malá písmena, bez diakritiky, bez čísel dílů
     * („ep. 12" / „#12" / „díl 12" / „12." na začátku) a interpunkce, zbylé tokeny.
     */
    fun normalizeTitle(raw: String): String {
        val noDiacritics = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noDiacritics
            .replace(Regex("\\b(ep|epizoda|episode|dil|díl|cast|part|#)\\s*\\.?\\s*\\d+"), " ")
            .replace(Regex("^\\s*\\d+[.)]?\\s+"), " ")          // vedoucí pořadové číslo
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokens(s: String): Set<String> =
        normalizeTitle(s).split(" ").filter { it.length >= 2 }.toSet()

    /** Jaccard podobnost tokenů názvu (0..1). */
    fun titleSimilarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size.toDouble()
        val union = ta.union(tb).size.toDouble()
        return inter / union
    }

    /** Rozdíl dat v dnech (nebo null, když některé chybí / nejde naparsovat). */
    private fun daysApart(a: String?, b: String?): Long? {
        val ta = PodcastTimelineViewModel.parseEpisodeDate(a) ?: return null
        val tb = PodcastTimelineViewModel.parseEpisodeDate(b) ?: return null
        return Math.abs(ta - tb) / (24L * 60 * 60 * 1000)
    }

    /**
     * Spáruje audio a video epizody. Pro každou audio epizodu najde NEJLEPŠÍ dosud nepoužitou video
     * epizodu, jejíž název je dost podobný ([TITLE_THRESHOLD]) a datum blízko ([MAX_DAYS_APART], pokud
     * jsou data známá). Nespárované audio i video se ZACHOVAJÍ jako samostatné řádky (nic se neztratí).
     * Výsledek seřazen sestupně dle data (audio preferováno).
     */
    fun pairEpisodes(audio: List<SourceEpisode>, video: List<SourceEpisode>): List<MergedEpisode> {
        val usedVideo = HashSet<String>()
        val merged = ArrayList<MergedEpisode>(audio.size + video.size)

        for (a in audio) {
            var best: SourceEpisode? = null
            var bestSim = TITLE_THRESHOLD
            for (v in video) {
                if (v.id in usedVideo) continue
                val sim = titleSimilarity(a.title, v.title)
                if (sim < bestSim) continue
                val dd = daysApart(a.date, v.date)
                if (dd != null && dd > MAX_DAYS_APART) continue   // data známá a daleko → ne
                best = v; bestSim = sim
            }
            if (best != null) usedVideo.add(best.id)
            merged.add(
                MergedEpisode(
                    title = a.title,
                    date = a.date,                                 // audio/RSS datum = správnější
                    imageUrl = a.imageUrl ?: best?.imageUrl,
                    description = a.description ?: best?.description,
                    durationSec = a.durationSec.takeIf { it > 0 } ?: (best?.durationSec ?: 0.0),
                    audio = a,
                    video = best,
                ),
            )
        }
        // Video bez audio protějšku → samostatný řádek (jen video; lze i poslech přes YT audio proxy).
        for (v in video) {
            if (v.id in usedVideo) continue
            merged.add(
                MergedEpisode(
                    title = v.title,
                    date = v.date,
                    imageUrl = v.imageUrl,
                    description = v.description,
                    durationSec = v.durationSec,
                    audio = v,        // YT epizoda umí i audio (proxy) → „Poslech"
                    video = v,
                ),
            )
        }
        return merged.sortedByDescending {
            PodcastTimelineViewModel.parseEpisodeDate(it.date) ?: Long.MIN_VALUE
        }
    }

    /**
     * Auto-návrh: pro [source] najde mezi [others] nejpravděpodobnějšího kandidáta TÉHOŽ pořadu
     * jiného typu (RSS↔YouTube) dle podobnosti NÁZVU ZDROJE. Vrací jen dost silnou shodu, jinak null.
     */
    fun suggestMatch(source: PodcastSource, others: List<PodcastSource>): PodcastSource? =
        others
            .filter { it.type != source.type }   // audio se páruje s videem, ne audio s audiem
            .map { it to titleSimilarity(source.title, it.title) }
            .filter { it.second >= SOURCE_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first

    private const val TITLE_THRESHOLD = 0.5     // podobnost názvu epizody (Jaccard)
    private const val SOURCE_THRESHOLD = 0.4    // podobnost názvu zdroje (mírnější — názvy kanálů kratší)
    private const val MAX_DAYS_APART = 14L      // audio/video téže epizody bývá pár dní od sebe
}
