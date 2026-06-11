package com.github.jankoran90.showlyfin.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import android.content.Context
import com.github.jankoran90.showlyfin.MainActivity

/** RELAY — domácí widget „Poslouchej": ovládání ABS audio přehrávače (media3). */
class ListenWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = ListenRemote.load(context)
        provideContent { ListenContent(state) }
    }

    @Composable
    private fun ListenContent(state: ListenRemote.State) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.Background)
                .cornerRadius(20.dp)
                .padding(14.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "POSLOUCHEJ",
                    modifier = GlanceModifier.defaultWeight()
                        .clickable(actionStartActivity<MainActivity>()),
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Accent),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                WidgetControl(
                    glyph = WidgetGlyphs.REFRESH,
                    onClick = actionRunCallback<ListenRefreshAction>(),
                    size = 30,
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            if (state.connected) {
                Text(
                    text = state.title ?: "Přehrávání",
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.OnBackground),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (!state.subtitle.isNullOrBlank()) {
                    Text(
                        text = state.subtitle,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.OnBackgroundMuted),
                            fontSize = 12.sp,
                        ),
                    )
                }
                Spacer(GlanceModifier.height(10.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WidgetControl(WidgetGlyphs.REWIND, actionRunCallback<ListenRewindAction>())
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(
                        glyph = if (state.isPlaying) WidgetGlyphs.PAUSE else WidgetGlyphs.PLAY,
                        onClick = actionRunCallback<ListenPlayPauseAction>(),
                        accent = true,
                        size = 52,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(WidgetGlyphs.FORWARD, actionRunCallback<ListenForwardAction>())
                }
            } else {
                Text(
                    text = "Nic se nepřehrává",
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.OnBackgroundMuted),
                        fontSize = 14.sp,
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Otevřít Showlyfin →",
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Accent),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}
