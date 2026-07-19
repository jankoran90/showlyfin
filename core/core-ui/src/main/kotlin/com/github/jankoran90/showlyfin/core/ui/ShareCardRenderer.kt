package com.github.jankoran90.showlyfin.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/**
 * PLAKÁT (SHW-98) — vygeneruje sdílecí kartu filmu jako Bitmap na výšku telefonu.
 * Čistý android.graphics.Canvas (žádný Compose/lifecycle) = deterministický render bez čekání na frame,
 * spolehlivý od minSdk 23. Barvy jsou EXPORT-asset konstanty (brand: AMOLED černá + oranžový akcent,
 * ČSFD amber) — vědomá odchylka od theme tokenů, obdoba lokálních amber konstant v CsfdComponents.
 * Vstupní data + předem načtené bitmapy dodá [ShareCard.shareFilm]; tento objekt jen kreslí.
 */
data class ShareReview(val author: String, val stars: Int?, val text: String)   // stars = ČSFD 0-100

data class ShareCardData(
    val title: String,
    val year: Int?,
    val csfdPct: Int?,
    val directors: List<String>,
    val genres: List<String>,
    val description: String?,
    val reviews: List<ShareReview>,
)

object ShareCardRenderer {
    // ── Export-asset paleta (brand, ne theme) ──
    private const val BG        = 0xFF000000.toInt()   // AMOLED černá
    private const val ACCENT    = 0xFFFF8C1A.toInt()   // brand oranžová (věrný akcent)
    private const val TEXT      = 0xFFFFFFFF.toInt()
    private const val TEXT_DIM  = 0xFFB4B4B4.toInt()
    private const val CARD_BG   = 0xFF161616.toInt()
    private const val CSFD_AMBER = 0xFFF5A623.toInt()  // konzistentní s CsfdComponents

    // ── Rozměry (design-jednotky, pevná šířka; výška dynamická dle obsahu) ──
    // Celý render probíhá v těchto jednotkách; výsledný bitmap je SCALE× větší (supersampling)
    // = ostrý vektorový text/tvary/badge bez přepočítávání každé konstanty. 2× = 2160px výstup.
    private const val SCALE = 2
    private const val W = 1080
    private const val PAD = 56
    private const val HEADER_H = 608          // 16:9 fanart přes celou šířku
    private const val POSTER_W = 300
    private const val POSTER_H = 450          // 2:3
    private const val POSTER_OVERLAP = 250    // o kolik poster přesahuje pod fanart
    private const val CONTENT_W = W - 2 * PAD

    private fun tp(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun stars(pct: Int?): String {
        if (pct == null) return ""
        val full = (pct / 20).coerceIn(0, 5)
        return "★".repeat(full) + "☆".repeat(5 - full)
    }

    /** Vyrenderuje kartu. [poster]/[backdrop] mohou být null (fallback bez obrázku). */
    fun render(data: ShareCardData, poster: Bitmap?, backdrop: Bitmap?): Bitmap {
        // ── Měření textových bloků předem (kvůli dynamické výšce) ──
        val titlePaint = tp(58f, TEXT, bold = true)
        val metaPaint = tp(30f, TEXT_DIM)
        val dirLabelPaint = tp(28f, ACCENT, bold = true)
        val dirPaint = tp(30f, TEXT)
        val descPaint = tp(31f, TEXT_DIM)
        val revAuthorPaint = tp(28f, TEXT, bold = true)
        val revStarPaint = tp(28f, CSFD_AMBER)
        val revTextPaint = tp(29f, TEXT_DIM)
        val footPaint = tp(30f, ACCENT, bold = true)

        // Info vedle posteru (vpravo od něj, uvnitř headeru)
        val infoX = PAD + POSTER_W + 28
        val infoW = W - infoX - PAD
        val titleL = layout(data.title, titlePaint, infoW, 3)

        val metaParts = buildList {
            data.year?.let { add(it.toString()) }
            if (data.genres.isNotEmpty()) add(data.genres.take(3).joinToString(" · "))
        }
        val metaL = if (metaParts.isNotEmpty()) layout(metaParts.joinToString("  •  "), metaPaint, infoW, 2) else null

        // Obsah pod headerem
        val dirText = data.directors.take(2).joinToString(", ")
        val descL = data.description?.takeIf { it.isNotBlank() }?.let { layout(it, descPaint, CONTENT_W, 9) }

        // Recenze karty
        val revCardW = CONTENT_W
        val revInnerW = revCardW - 2 * 28
        class Rev(val author: StaticLayout, val starStr: String, val text: StaticLayout, val h: Int)
        val revs = data.reviews.take(2).map { r ->
            val a = layout(r.author, revAuthorPaint, revInnerW, 1)
            val t = layout(r.text.replace(Regex("\\s+"), " ").trim(), revTextPaint, revInnerW, 4)
            Rev(a, stars(r.stars), t, 28 + a.height + 10 + t.height + 28)
        }

        // ── Výpočet výšky ──
        var y = HEADER_H + (POSTER_H - POSTER_OVERLAP) + 44   // pod spodní hranou posteru + mezera
        if (dirText.isNotEmpty()) y += 46
        if (descL != null) y += descL.height + 40
        if (revs.isNotEmpty()) { y += 8; revs.forEach { y += it.h + 20 } }
        y += 24 + 56          // footer
        val totalH = y + PAD

        val bmp = Bitmap.createBitmap(W * SCALE, totalH * SCALE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(SCALE.toFloat(), SCALE.toFloat())   // vše dál kreslí v design-jednotkách, rasterizuje 2×
        c.drawColor(BG)

        // ── Header fanart (center-crop) + scrim ──
        val headerRect = Rect(0, 0, W, HEADER_H)
        if (backdrop != null) {
            drawCropped(c, backdrop, headerRect)
        } else {
            c.drawRect(headerRect, Paint().apply { color = CARD_BG })
        }
        // gradient scrim dolů do černé (čitelnost + splynutí s tělem)
        val scrim = Paint().apply {
            shader = LinearGradient(0f, HEADER_H * 0.35f, 0f, HEADER_H.toFloat(),
                0x00000000, BG, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, HEADER_H * 0.35f, W.toFloat(), HEADER_H.toFloat(), scrim)

        // ── Poster (přesahuje spodek headeru), zaoblený, s rámečkem ──
        val posterX = PAD
        val posterY = HEADER_H - POSTER_OVERLAP
        val posterRect = RectF(posterX.toFloat(), posterY.toFloat(),
            (posterX + POSTER_W).toFloat(), (posterY + POSTER_H).toFloat())
        val clip = c.save()
        val path = android.graphics.Path().apply { addRoundRect(posterRect, 20f, 20f, android.graphics.Path.Direction.CW) }
        c.clipPath(path)
        if (poster != null) {
            drawCropped(c, poster, Rect(posterX, posterY, posterX + POSTER_W, posterY + POSTER_H))
        } else {
            c.drawRect(posterRect, Paint().apply { color = CARD_BG })
        }
        c.restoreToCount(clip)
        c.drawRoundRect(posterRect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x33FFFFFF
        })

        // ── Info vedle posteru (dole zarovnané se spodkem posteru) ──
        titlePaint.setShadowLayer(8f, 0f, 2f, 0xCC000000.toInt())
        val infoBottom = posterY + POSTER_H
        var infoContentH = titleL.height
        if (metaL != null) infoContentH += 14 + metaL.height
        if (data.csfdPct != null) infoContentH += 18 + 52
        var iy = (infoBottom - infoContentH).coerceAtLeast(HEADER_H - POSTER_OVERLAP)
        c.save(); c.translate(infoX.toFloat(), iy.toFloat()); titleL.draw(c); c.restore()
        iy += titleL.height
        if (metaL != null) {
            iy += 14
            c.save(); c.translate(infoX.toFloat(), iy.toFloat()); metaL.draw(c); c.restore()
            iy += metaL.height
        }
        titlePaint.clearShadowLayer()
        if (data.csfdPct != null) {
            iy += 18
            drawCsfdBadge(c, infoX.toFloat(), iy.toFloat(), data.csfdPct)
        }

        // ── Tělo pod headerem ──
        y = infoBottom + 44
        if (dirText.isNotEmpty()) {
            c.drawText("REŽIE", PAD.toFloat(), y + 24f, dirLabelPaint)
            c.drawText(dirText, PAD + 130f, y + 26f, dirPaint)
            y += 46
        }
        if (descL != null) {
            c.save(); c.translate(PAD.toFloat(), y.toFloat()); descL.draw(c); c.restore()
            y += descL.height + 40
        }
        if (revs.isNotEmpty()) {
            y += 8
            for (r in revs) {
                val cardRect = RectF(PAD.toFloat(), y.toFloat(), (W - PAD).toFloat(), (y + r.h).toFloat())
                c.drawRoundRect(cardRect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_BG })
                // levý akcentní proužek
                c.drawRoundRect(RectF(PAD.toFloat(), y.toFloat(), PAD + 8f, (y + r.h).toFloat()), 4f, 4f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
                var ry = y + 28
                c.save(); c.translate(PAD + 28f, ry.toFloat()); r.author.draw(c); c.restore()
                if (r.starStr.isNotEmpty()) {
                    val sw = revStarPaint.measureText(r.starStr)
                    c.drawText(r.starStr, (W - PAD - 28) - sw, ry + r.author.height - 6f, revStarPaint)
                }
                ry += r.author.height + 10
                c.save(); c.translate(PAD + 28f, ry.toFloat()); r.text.draw(c); c.restore()
                y += r.h + 20
            }
        }

        // ── Footer watermark ──
        y += 24
        val foot = "🎬  Filmy"
        val fw = footPaint.measureText(foot)
        c.drawText(foot, (W - fw) / 2f, y + 40f, footPaint)

        return bmp
    }

    /** ČSFD % badge — pill s hvězdami + procenty. */
    private fun drawCsfdBadge(c: Canvas, x: Float, y: Float, pct: Int) {
        val starStr = stars(pct)
        val pctStr = "$pct %"
        val starP = tp(32f, CSFD_AMBER)
        val pctP = tp(34f, TEXT, bold = true)
        val sw = starP.measureText(starStr)
        val pw = pctP.measureText(pctStr)
        val h = 52f
        val pill = RectF(x, y, x + 36 + sw + 16 + pw + 36, y + h)
        c.drawRoundRect(pill, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt() })
        c.drawText(starStr, x + 30, y + 37, starP)
        c.drawText(pctStr, x + 30 + sw + 16, y + 38, pctP)
    }

    /** Nakreslí [src] center-crop do [dst] (zachová poměr, ořízne přebytek). */
    private fun drawCropped(c: Canvas, src: Bitmap, dst: Rect) {
        val sw = src.width.toFloat(); val sh = src.height.toFloat()
        val dw = dst.width().toFloat(); val dh = dst.height().toFloat()
        val scale = maxOf(dw / sw, dh / sh)
        val vw = dw / scale; val vh = dh / scale
        val sx = ((sw - vw) / 2f)
        val sy = ((sh - vh) / 2f)
        val srcRect = Rect(sx.toInt(), sy.toInt(), (sx + vw).toInt(), (sy + vh).toInt())
        c.drawBitmap(src, srcRect, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
