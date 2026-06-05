package com.github.jankoran90.showlyfin

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
import com.github.jankoran90.showlyfin.ui.phone.ShowlyfinPhoneApp
import com.github.jankoran90.showlyfin.ui.tv.ShowlyfinTvApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var traktAuthManager: TraktAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        setContent {
            if (isTV) {
                ShowlyfinTvApp()
            } else {
                ShowlyfinPhoneApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "showlyfin" && uri.host == "trakt") {
            val code = uri.getQueryParameter("code") ?: return
            traktAuthManager.onAuthCode(code)
        }
    }
}
