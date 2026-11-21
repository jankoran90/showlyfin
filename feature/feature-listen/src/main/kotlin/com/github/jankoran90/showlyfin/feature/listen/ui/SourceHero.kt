package com.github.jankoran90.showlyfin.feature.listen.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.tokens.CoverSize
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing

/**
 * AMPHORA (SHW-124) F8 / DS2 — jak se hlavička detailu kreslí (Nastavení › Domů a vzhled, poskytuje
 * shell Slova). [backdrop] = rozmazaný obal za hlavičkou, [tint] = nádech barvy z obalu.
 */
data class SourceHeroPrefs(val backdrop: Boolean = true, val tint: Boolean = true)

val LocalSourceHeroPrefs = staticCompositionLocalOf { SourceHeroPrefs() }

/**
 * AMPHORA (SHW-124) F8 / DS2 „obal jako hrdina“ — jedna hlavička pro detail pořadu, knihy, série,
 * RSS, YouTube kanálu a pořadu ČT: velký obal uprostřed, pod ním název a podtitulek, za tím
 * rozmazaný obal přecházející do pozadí (blur jde až od Androidu 12, starší dostanou tlumený obal
 * pod závojem). [wide] = obal 16:9 (YouTube, ČT). [content] = čipy a akce pod textem.
 */
@Composable
fun SourceHero(
    coverUrl: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    placeholder: ImageVector = Icons.Rounded.Headphones,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val prefs = LocalSourceHeroPrefs.current
    val tint = rememberCoverTint(coverUrl, prefs.tint)
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        if (prefs.backdrop && coverUrl != null) {
            val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .then(if (blurOk) Modifier.blur(Spacing.xxl) else Modifier)
                    .alpha(if (blurOk) BACKDROP_ALPHA else BACKDROP_ALPHA_NO_BLUR),
            )
        }
        // Závoj: nahoře nádech barvy obalu (nebo tlumené pozadí), dole plynule do pozadí obrazovky.
        val top = tint?.copy(alpha = TINT_ALPHA) ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = VEIL_TOP_ALPHA)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(top, bg.copy(alpha = VEIL_MID_ALPHA), bg))))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            val coverModifier = if (wide) {
                Modifier.fillMaxWidth(WIDE_COVER_FRACTION).aspectRatio(16f / 9f)
            } else {
                Modifier.size(CoverSize.hero)
            }
            Box(
                coverModifier
                    .shadow(Spacing.s, MaterialTheme.shapes.large)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
    }
}

private const val BACKDROP_ALPHA = 0.55f
private const val BACKDROP_ALPHA_NO_BLUR = 0.25f
private const val TINT_ALPHA = 0.35f
private const val VEIL_TOP_ALPHA = 0.4f
private const val VEIL_MID_ALPHA = 0.6f
private const val WIDE_COVER_FRACTION = 0.8f

/** „1 díl“ / „3 díly“ / „12 dílů“ (podtitulek hlavičky). */
fun episodeCountLabel(n: Int): String = when {
    n == 1 -> "1 díl"
    n in 2..4 -> "$n díly"
    else -> "$n dílů"
}
