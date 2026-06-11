package com.github.jankoran90.showlyfin.widget

import android.content.Context
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
import com.github.jankoran90.showlyfin.MainActivity

/** RELAY — domácí widget „Sleduj": dálkové ovládání běžící Jellyfin session (Wolphin/Yellyfin na TV). */
class WatchWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = WatchRemote.load(context)
        provideContent { WatchContent(state) }
    }

    @Composable
    private fun WatchContent(state: WatchRemote.State) {
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
                    text = "SLEDUJ",
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
                    onClick = actionRunCallback<WatchRefreshAction>(),
                    size = 30,
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            when {
                state.noCreds -> Hint("Jellyfin není přihlášen")
                !state.hasSession -> Hint("Žádná aktivní TV session")
                else -> {
                    Text(
                        text = state.deviceName ?: "Jellyfin TV",
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.OnBackgroundMuted),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = if (state.hasNowPlaying) state.title!! else "Nic nehraje na TV",
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
                        WidgetControl(WidgetGlyphs.REWIND, actionRunCallback<WatchRewindAction>())
                        Spacer(GlanceModifier.width(10.dp))
                        WidgetControl(
                            glyph = if (state.playing) WidgetGlyphs.PAUSE else WidgetGlyphs.PLAY,
                            onClick = actionRunCallback<WatchPlayPauseAction>(),
                            accent = true,
                            size = 50,
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        WidgetControl(WidgetGlyphs.FORWARD, actionRunCallback<WatchForwardAction>())
                        Spacer(GlanceModifier.width(10.dp))
                        WidgetControl(WidgetGlyphs.STOP, actionRunCallback<WatchStopAction>())
                    }
                }
            }
        }
    }

    @Composable
    private fun Hint(text: String) {
        Text(
            text = text,
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
