package com.github.jankoran90.showlyfin.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** RELAY — receiver „Sleduj" widgetu (registrace v AndroidManifest). */
class WatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchWidget()
}
