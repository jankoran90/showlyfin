package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.net.Uri
import org.json.JSONObject

/**
 * Nákup na stránce Audioteky uvnitř Slova → po zaplacení samo dál (user 2026-11-18 19:52: „chci koupit a už nic
 * nedělat"). Čistá logika bez WebView: z adres stránek pozná kupovanou knihu a návrat z pokladny; skripty pro
 * Poličku najdou u té knihy tlačítko Stáhnout (a v okně formátu MP3) a ťuknou. Jejich stránky po zaplacení zatím
 * neznáme → adresy se zapisují ([AudiotekaPrefs.addPurchasePage]) a posílají na server, ať jde doladit.
 */
class AudiotekaPurchaseFlow {

    /** Slug knihy z poslední navštívené stránky knihy (`/cz/audiokniha/<slug>/`). */
    var productSlug: String? = null
        private set

    /** Byl jsi v košíku / pokladně (od posledního návratu z ní). */
    var checkoutSeen: Boolean = false
        private set

    sealed interface Event {
        data object None : Event
        /** Nová stránka knihy → návrh knihovny podle její kategorie. */
        data class Product(val slug: String) : Event
        /** První stránka pokladny (zapsat průběh nákupu na server). */
        data object CheckoutStarted : Event
        /** Po pokladně přišla stránka poděkování / dokončení → otevřít Poličku. */
        data object PurchaseFinished : Event
    }

    /** Řádek do záznamu průběhu: host + cesta, BEZ parametrů (tokeny plateb, osobní údaje). */
    fun describe(url: String): String? {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (u.scheme != "http" && u.scheme != "https") return null
        return (u.host.orEmpty() + u.path.orEmpty()).take(200)
    }

    fun onPage(url: String): Event {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return Event.None
        val host = u.host.orEmpty()
        val path = u.path.orEmpty().lowercase()
        if (host.endsWith("audioteka.com")) {
            PRODUCT.find(path)?.groupValues?.get(1)?.let { slug ->
                if (slug != productSlug) {
                    productSlug = slug
                    return Event.Product(slug)
                }
                return Event.None
            }
            if (checkoutSeen && THANKS.containsMatchIn(path) && !CHECKOUT_ONLY.containsMatchIn(path)) {
                checkoutSeen = false
                return Event.PurchaseFinished
            }
        }
        if (CHECKOUT.containsMatchIn(path) || (!host.endsWith("audioteka.com") && host.isNotEmpty() && checkoutSeen)) {
            val first = !checkoutSeen
            checkoutSeen = true
            return if (first) Event.CheckoutStarted else Event.None
        }
        return Event.None
    }

    /** Polička otevřená po koupi: samo-ťuknutí proběhne nejvýš jednou. */
    fun consumeCheckout() { checkoutSeen = false }

    companion object {
        private val PRODUCT = Regex("/audiokniha/([a-z0-9-]+)")
        private val CHECKOUT = Regex("kosik|pokladn|objednavk|checkout|platb|payment|cart")
        private val THANKS = Regex("dekuj|podekovan|dokonc|uspesn|success|thank|complete")
        // „potvrzení" bývá i souhrn PŘED platbou → samo o sobě konec nákupu neznamená.
        private val CHECKOUT_ONLY = Regex("souhrn|summary")

        /**
         * Krok 1 v Poličce: najde položku knihy (odkaz se slugem, jinak název v textu položky) a ťukne na její
         * Stáhnout. Položka = nejbližší předek tlačítka, ve kterém je jen jedno Stáhnout. Cizí knihu nikdy:
         * bez shody nic neťukne a vrátí diagnostiku (počet tlačítek, texty položek) pro doladění.
         */
        fun clickDownloadJs(slug: String?, title: String?): String = """
            (function(slug, title){
              function norm(s){return (s||'').normalize('NFD').replace(/\p{M}/gu,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();}
              var t = norm(title);
              function dl(el){ var x = norm(el.innerText||el.textContent); return x.indexOf('stahnout')===0 && x.length<30 && x.indexOf('mp3')<0; }
              var all = Array.prototype.slice.call(document.querySelectorAll('button,a,[role=button]'));
              var btns = all.filter(dl);
              var items = [];
              for (var i=0;i<btns.length;i++){
                var n = btns[i];
                for (var d=0; d<10 && n; d++){
                  n = n.parentElement; if(!n) break;
                  var cnt = Array.prototype.filter.call(n.querySelectorAll('button,a,[role=button]'), dl).length;
                  if (cnt>1) break;
                  var txt = norm(n.innerText);
                  var bySlug = slug && n.querySelector('a[href*="/'+slug+'"]');
                  var byTitle = t && t.length>3 && txt.indexOf(t)>=0;
                  if (bySlug || byTitle){ btns[i].click(); return JSON.stringify({ok:true, how: bySlug?'slug':'title', buttons: btns.length}); }
                  if (d===4) items.push(txt.substring(0,60));
                }
              }
              return JSON.stringify({ok:false, buttons: btns.length, items: items.slice(0,8), url: location.pathname});
            })(${JSONObject.quote(slug.orEmpty())}, ${JSONObject.quote(title.orEmpty())})
        """.trimIndent()

        /** Krok 2: okno formátu („Stáhnout MP3") → ťuknout. Vrátí true, když tlačítko našel. */
        const val CLICK_MP3_JS = """
            (function(){
              function norm(s){return (s||'').normalize('NFD').replace(/\p{M}/gu,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();}
              var b = Array.prototype.slice.call(document.querySelectorAll('button,a,[role=button]'))
                .filter(function(el){ var x = norm(el.innerText||el.textContent); return x.indexOf('mp3')>=0 && x.length<40; })[0];
              if (b) { b.click(); return true; }
              return false;
            })()
        """
    }
}
