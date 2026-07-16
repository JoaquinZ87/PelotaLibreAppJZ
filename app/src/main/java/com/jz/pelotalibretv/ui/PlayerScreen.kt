package com.jz.pelotalibretv.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Reproductor: WebView blindado a pantalla completa que carga el embed.
 * Defensas: sin ventanas nuevas (popups off), bloquea navegación a OTRO host (ads/redirects),
 * autoplay habilitado, contenido mixto permitido (el HLS puede venir por http).
 * Se le pasa un [referer] (la página del sitio) porque muchos embeds lo exigen.
 * BACK cierra el reproductor.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(embedUrl: String, referer: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val isTv = remember { context.isTvDevice() }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)

            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webChromeClient = object : WebChromeClient() {
                // Bloquea popups / pop-unders (window.open, target=_blank).
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
                ): Boolean = false
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url ?: return false
                    // Solo controlamos la navegación de la página principal (no subrecursos).
                    if (request.isForMainFrame) {
                        val target = url.host ?: return true
                        val allowed = Uri.parse(embedUrl).host ?: return true
                        return !hostMatches(target, allowed) // true = cancelar (bloquear ad/redirect)
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view ?: return
                    // Arranque automático de la reproducción:
                    if (isTv) {
                        // TV (control remoto): flecha ARRIBA para enfocar el play, luego OK (centro).
                        view.requestFocus()
                        view.postDelayed({ simulateKey(view, KeyEvent.KEYCODE_DPAD_UP) }, 1600)
                        view.postDelayed({ simulateKey(view, KeyEvent.KEYCODE_DPAD_CENTER) }, 2100)
                    } else {
                        // Celular (táctil): un toque real en el centro.
                        view.postDelayed({ simulateCenterTap(view) }, 1800)
                    }
                    // Desmutear (arranca en mute); reintentos por si el <video> tarda en existir.
                    view.postDelayed({ unmute(view) }, 3000)
                    view.postDelayed({ unmute(view) }, 4800)
                    view.postDelayed({ unmute(view) }, 7000)
                }
            }

            val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()
            loadUrl(embedUrl, headers)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { webView }
    )
}

/** Compara hosts ignorando www. y aceptando subdominios del embed. */
private fun hostMatches(target: String, allowed: String): Boolean {
    val a = allowed.removePrefix("www.")
    val t = target.removePrefix("www.")
    return t == a || t.endsWith(".$a") || a.endsWith(".$t")
}

/** Desmutea y sube el volumen de cualquier <video> del player (y de Clappr si está expuesto). */
private fun unmute(webView: WebView) {
    webView.evaluateJavascript(
        "(function(){try{" +
            "var v=document.getElementsByTagName('video');" +
            "for(var i=0;i<v.length;i++){v[i].muted=false;v[i].volume=1.0;try{v[i].play();}catch(e){}}" +
            "if(window.player){try{if(window.player.setVolume)window.player.setVolume(100);}catch(e){}" +
            "try{if(window.player.unmute)window.player.unmute();}catch(e){}}" +
            "}catch(e){}})();",
        null
    )
}

/** Envía una tecla del control remoto al WebView (D-pad), como si la apretara el usuario. */
private fun simulateKey(webView: WebView, keyCode: Int) {
    val t = SystemClock.uptimeMillis()
    webView.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
    webView.dispatchKeyEvent(KeyEvent(t, t + 40, KeyEvent.ACTION_UP, keyCode, 0))
}

/** Dispara un toque real en el centro del WebView (para arrancar el player). */
private fun simulateCenterTap(webView: WebView) {
    if (webView.width == 0 || webView.height == 0) return
    val x = webView.width / 2f
    val y = webView.height / 2f
    val t = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0)
    webView.dispatchTouchEvent(down)
    webView.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
}
