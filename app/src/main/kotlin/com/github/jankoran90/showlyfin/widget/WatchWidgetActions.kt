package com.github.jankoran90.showlyfin.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** RELAY — ActionCallbacky „Sleduj" widgetu (dálkové ovládání JF session). Po příkazu překresli. */

class WatchPlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchRemote.playPause(context)
        WatchWidget().update(context, glanceId)
    }
}

class WatchRewindAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchRemote.rewind(context)
        WatchWidget().update(context, glanceId)
    }
}

class WatchForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchRemote.forward(context)
        WatchWidget().update(context, glanceId)
    }
}

class WatchStopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchRemote.stop(context)
        WatchWidget().update(context, glanceId)
    }
}

class WatchRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchWidget().update(context, glanceId)
    }
}
