package com.jz.pelotalibretv

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jz.pelotalibretv.ui.PlayerScreen
import com.jz.pelotalibretv.ui.theme.PelotaLibreTvTheme
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class PlayerTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("embedUrl")?.toHttpUrlOrNull()
        if (url == null || !url.isHttps) {
            finish()
            return
        }
        WebView.setWebContentsDebuggingEnabled(true)
        setContent {
            PelotaLibreTvTheme {
                PlayerScreen(url.toString(), intent.getStringExtra("referer").orEmpty()) { finish() }
            }
        }
    }
}
