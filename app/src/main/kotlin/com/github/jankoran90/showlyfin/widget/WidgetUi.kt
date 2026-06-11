package com.github.jankoran90.showlyfin.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** Kruhové ovládací tlačítko se znakovým glyfem (sdílené Poslouchej i Sleduj). */
@Composable
internal fun WidgetControl(
    glyph: String,
    onClick: Action,
    accent: Boolean = false,
    size: Int = 44,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .cornerRadius((size / 2).dp)
            .background(if (accent) WidgetColors.Accent else WidgetColors.ControlBg)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                color = ColorProvider(if (accent) WidgetColors.OnAccent else WidgetColors.OnBackground),
                fontSize = (size * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

internal object WidgetGlyphs {
    const val PLAY = "▶"          // ▶
    const val PAUSE = "⏸"         // ⏸
    const val PREV = "⏮"          // ⏮
    const val NEXT = "⏭"          // ⏭
    const val STOP = "⏹"          // ⏹
    const val REWIND = "⏪"        // ⏪
    const val FORWARD = "⏩"       // ⏩
    const val REFRESH = "↻"       // ↻
}
