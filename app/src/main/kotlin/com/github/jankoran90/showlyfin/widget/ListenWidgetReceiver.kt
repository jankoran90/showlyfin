package com.github.jankoran90.showlyfin.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** RELAY — receiver „Poslouchej" widgetu (registrace v AndroidManifest). */
class ListenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListenWidget()
}
