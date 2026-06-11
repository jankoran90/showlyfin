package com.github.jankoran90.showlyfin.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** RELAY — ActionCallbacky „Poslouchej" widgetu. Po každém příkazu překresli widget. */

class ListenPlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ListenRemote.playPause(context)
        ListenWidget().update(context, glanceId)
    }
}

class ListenRewindAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ListenRemote.rewind(context)
        ListenWidget().update(context, glanceId)
    }
}

class ListenForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ListenRemote.forward(context)
        ListenWidget().update(context, glanceId)
    }
}

class ListenRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ListenWidget().update(context, glanceId)
    }
}
