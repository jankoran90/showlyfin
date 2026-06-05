package com.github.jankoran90.showlyfin

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.jankoran90.showlyfin.ui.phone.ShowlyfinPhoneApp
import com.github.jankoran90.showlyfin.ui.tv.ShowlyfinTvApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        setContent {
            if (isTV) {
                ShowlyfinTvApp()
            } else {
                ShowlyfinPhoneApp()
            }
        }
    }
}
