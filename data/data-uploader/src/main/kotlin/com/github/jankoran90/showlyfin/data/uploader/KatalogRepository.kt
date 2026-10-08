package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * LABYRINT (FLM-04, user 2026-10-08: „to objevování a žánry ať je i v appce filmy… ať je to zase
 * paritnější web–app") — klient serverového katalogu (`routes/katalog.py`, `routes/katalog_protebe.py`
 * v jellyfin-uploader). Žánrová mapa (hlavní žánry + podžánry a nálady z ověřených TMDB klíčových
 * slov), procházení celého TMDB se stavem profilu, profily titulů a stránka „Pro tebe".
 *
 * Logika žije na serveru, appka i web ji jen zobrazují — proto ukazují totéž. Profil = JF user id
 * (server si klíč znormalizuje, viz `_resolve_profile_key`).
 */
@Singleton
class KatalogRepository @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    data class Zanr(val id: String, val label: String)
    data class Podzanr(val id: String, val label: String, val parent: String?, val nalada: Boolean)
    data class Mapa(val hlavni: List<Zanr>, val podzanry: List<Podzanr>)

    /** Titul z katalogu + stav profilu (viděl / moje hodnocení / Chci vidět / uložený zdroj). */
    data class Titul(
        val klic: String,
        val isShow: Boolean,
        val tmdbId: Long,
        val nazev: String,
        val rok: Int?,
        /** TMDB poster_path („/abc.jpg") — MediaItem si z něj URL složí sám. */
        val posterPath: String?,
        val hodnoceni: Float?,
        val popis: String,
        val videno: Boolean,
        val mojeHodnoceni: Int?,
        val chci: Boolean,
        val zdroj: Boolean,
        val role: String? = null,
        /** Má českou/slovenskou zvukovou stopu (jen u titulů z vlastní Filmotéky — `vlastni`). */
        val cz: Boolean = false,
        /** Hlavní žánr (Tragikomedie, Akční komedie…) — jen u `vlastni`, pro řádky stránky „Česky". */
        val hlavni: String? = null,
    )

    data class Strana(val tituly: List<Titul>, val stranka: Int, val stranek: Int)

    data class Radek(
        val id: String,
        val nazev: String,
        val proc: String,
        /** Odkaz na stránku katalogu („podzanr/cerny-humor"), nebo null. */
        val odkaz: String?,
        val tituly: List<Titul>,
    )

    data class Osoba(val id: Long, val jmeno: String)

    /** Profil titulu pro štítky na kartě. */
    data class Profil(
        val klic: String,
        val rok: Int?,
        val kanon: List<String>,
        val podzanry: List<String>,
        val zeme: List<String>,
        val rezie: List<Osoba>,
        val herci: List<Osoba>,
        val temata: List<Pair<Long, String>>,
        val hlavni: String?,
    )

    data class Stitek(val druh: String, val id: String, val label: String)

    private fun base(): String = prefs.getString("uploader_base_url", "").orEmpty().trim().trimEnd('/')
    private fun cookie(): String = prefs.getString("uploader_session_cookie", "").orEmpty()
    fun profil(): String = prefs.getString("jellyfin_user_id", "").orEmpty()

    @Volatile private var mapaCache: Mapa? = null
    private val profilCache = java.util.concurrent.ConcurrentHashMap<String, Profil>()

    private suspend fun get(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val base = base()
        if (base.isBlank()) return@withContext null
        runCatching {
            val req = Request.Builder().url(base + path)
                .apply { if (cookie().isNotBlank()) header("Cookie", "session=${cookie()}") }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            }
        }.onFailure { Timber.w(it, "[LABYRINT] GET %s selhal", path) }.getOrNull()
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val base = base()
        if (base.isBlank()) return@withContext null
        runCatching {
            val req = Request.Builder().url(base + path)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply { if (cookie().isNotBlank()) header("Cookie", "session=${cookie()}") }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            }
        }.onFailure { Timber.w(it, "[LABYRINT] POST %s selhal", path) }.getOrNull()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun mapa(): Mapa? {
        mapaCache?.let { return it }
        val j = get("/api/katalog/taxonomie") ?: return null
        val hl = j.optJSONArray("hlavni").objekty().map { Zanr(it.optString("id"), it.optString("label")) }
        val pod = j.optJSONArray("podzanry").objekty().map {
            Podzanr(
                id = it.optString("id"), label = it.optString("label"),
                parent = it.optString("parent").takeIf { p -> p.isNotBlank() && p != "null" },
                nalada = it.optString("druh") == "nalada",
            )
        }
        return Mapa(hl, pod).also { mapaCache = it }
    }

    /**
     * Výpis z celého katalogu pro stránku. [druh] = zanr | podzanr | tema | rok | zeme, [arg] jeho id.
     * [razeni] = hodnoceni | popularita | novinky | klenoty. null = server neodpověděl.
     */
    suspend fun objevuj(
        druh: String, arg: String, razeni: String = "hodnoceni", stranka: Int = 1,
        serialy: Boolean = false, jenCeske: Boolean = false, rokOd: Int? = null, rokDo: Int? = null,
        bezDetskych: Boolean = true,
    ): Strana? {
        val q = StringBuilder("profil=${enc(profil())}&razeni=$razeni&stranka=$stranka")
        when (druh) {
            "tema" -> q.append("&klicove=${enc(arg)}")
            "rok" -> q.append("&rok_od=${enc(arg)}&rok_do=${enc(arg)}")
            "zeme" -> q.append("&zeme=${enc(arg)}")
            else -> q.append("&$druh=${enc(arg)}")
        }
        if (serialy) q.append("&typ=tv")
        if (jenCeske && druh != "zeme") q.append("&zeme=CZ")
        rokOd?.let { if (druh != "rok") q.append("&rok_od=$it") }
        rokDo?.let { if (druh != "rok") q.append("&rok_do=$it") }
        if (bezDetskych) q.append("&bez_detskych=true")
        val j = get("/api/katalog/objevuj?$q") ?: return null
        return Strana(
            tituly = j.optJSONArray("tituly").objekty().mapNotNull { titul(it) },
            stranka = j.optInt("stranka", 1), stranek = j.optInt("stranek", 1),
        )
    }

    suspend fun proTebe(zamichat: Int, radku: Int, podzanru: Int, chci: Boolean, kurator: Boolean): List<Radek>? {
        val j = get(
            "/api/katalog/protebe?profil=${enc(profil())}&zamichat=$zamichat&radku=$radku" +
                "&podzanru=$podzanru&chci=$chci&kurator=$kurator",
        ) ?: return null
        return j.optJSONArray("radky").objekty().map { r ->
            Radek(
                id = r.optString("id"), nazev = r.optString("nazev"), proc = r.optString("proc"),
                odkaz = r.optString("odkaz").takeIf { it.isNotBlank() && it != "null" }?.removePrefix("#/"),
                tituly = r.optJSONArray("tituly").objekty().mapNotNull { titul(it) },
            )
        }
    }

    /** Žánry, podžánry a témata k hledanému výrazu (tituly a lidi hledá appka sama přes TMDB). */
    suspend fun hledatStitky(q: String): List<Stitek>? {
        if (q.trim().length < 2) return emptyList()
        val j = get("/api/katalog/hledat?q=${enc(q.trim())}&profil=${enc(profil())}") ?: return null
        val zanry = j.optJSONArray("zanry").objekty().map {
            Stitek(if (it.optString("druh") == "hlavni") "zanr" else "podzanr", it.optString("id"), it.optString("label"))
        }
        val temata = j.optJSONArray("temata").objekty().map {
            Stitek("tema", it.optLong("id").toString(), "# " + it.optString("nazev"))
        }
        return zanry + temata
    }

    suspend fun nazevTematu(id: String): String? = get("/api/katalog/tema/${enc(id)}")?.optString("nazev")

    /** „Máš k dispozici" — tituly z Filmotéky (uložené zdroje + JF knihovna) na téhle stránce. */
    suspend fun vlastni(druh: String, arg: String, jenCz: Boolean = false): List<Titul>? =
        get("/api/katalog/vlastni?profil=${enc(profil())}&druh=${enc(druh)}&arg=${enc(arg)}&jen_cz=$jenCz")
            ?.let { j -> j.optJSONArray("tituly").objekty().mapNotNull { titul(it) } }

    /**
     * Profily titulů (žánry, podžánry, rok, země, režie, herci, témata). Cache v paměti — štítky na
     * kartě i seskupení Filmotéky se ptají opakovaně. Co server ještě nestáhl, vrátí příště.
     */
    suspend fun profily(klice: List<String>, hybrid: Boolean = true): Map<String, Profil> {
        val chybi = klice.distinct().filter { it.isNotBlank() && !profilCache.containsKey(it) }
        for (davka in chybi.chunked(400)) {
            val body = JSONObject().put("profil", profil()).put("klice", JSONArray(davka)).put("hybrid", hybrid)
            val j = post("/api/katalog/tituly", body) ?: break
            val t = j.optJSONObject("tituly") ?: continue
            for (k in t.keys()) profilCache[k] = profilZ(k, t.getJSONObject(k))
        }
        return klice.mapNotNull { k -> profilCache[k]?.let { k to it } }.toMap()
    }

    private fun profilZ(k: String, o: JSONObject): Profil {
        fun osoby(a: JSONArray?) = a.objekty().map { Osoba(it.optLong("id"), it.optString("jmeno")) }
        val kw = o.optJSONArray("kw")
        val temata = (0 until (kw?.length() ?: 0)).mapNotNull { i ->
            val p = kw?.optJSONArray(i) ?: return@mapNotNull null
            p.optLong(0) to p.optString(1)
        }
        return Profil(
            klic = k, rok = o.optInt("rok", 0).takeIf { it > 0 },
            kanon = o.optJSONArray("kanon").retezce(), podzanry = o.optJSONArray("podzanry").retezce(),
            zeme = o.optJSONArray("zeme").retezce(), rezie = osoby(o.optJSONArray("rezie")),
            herci = osoby(o.optJSONArray("herci")), temata = temata,
            hlavni = o.optString("hlavni").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun titul(o: JSONObject): Titul? {
        val klic = o.optString("klic")
        val id = o.optLong("id", 0L)
        if (id <= 0L || klic.isBlank()) return null
        val stav = o.optJSONObject("stav") ?: JSONObject()
        val plakat = o.optString("plakat").takeIf { it.isNotBlank() && it != "null" }
        return Titul(
            klic = klic, isShow = o.optString("typ") == "tv", tmdbId = id,
            nazev = o.optString("nazev").takeIf { it.isNotBlank() && it != "null" } ?: o.optString("nazevOrig"),
            rok = o.optInt("rok", 0).takeIf { it > 0 },
            posterPath = plakat?.let { if (it.startsWith("/")) it else "/$it" },
            hodnoceni = o.optDouble("hodnoceni", 0.0).takeIf { it > 0.0 }?.toFloat(),
            popis = o.optString("popis").takeIf { it != "null" }.orEmpty(),
            videno = stav.optBoolean("videno", false),
            mojeHodnoceni = stav.optInt("hodnoceni", 0).takeIf { it > 0 },
            chci = stav.optBoolean("chci", false), zdroj = stav.optBoolean("zdroj", false),
            role = o.optString("role").takeIf { it.isNotBlank() && it != "null" },
            cz = o.optBoolean("cz", false),
            hlavni = o.optString("hlavni").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)   // „Pro tebe" poprvé skládá řádky několik sekund
            .build()

        private fun JSONArray?.objekty(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        private fun JSONArray?.retezce(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }
}
