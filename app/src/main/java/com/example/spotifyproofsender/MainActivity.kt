package com.example.spotifyproofsender

import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.spotifyproofsender.ui.StreamProofApp
import com.example.spotifyproofsender.ui.theme.StreamProofAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContent {
            StreamProofAppTheme {
                StreamProofApp()
            }
        }
    }
}
