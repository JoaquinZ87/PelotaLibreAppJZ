package com.jz.pelotalibretv.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jz.pelotalibretv.data.AppConfig
import com.jz.pelotalibretv.data.PlayerAdFilter
import com.jz.pelotalibretv.data.PlayerDocumentInterceptor
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalTvMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(embedUrl: String, referer: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val active = remember(embedUrl, referer) { AtomicBoolean(true) }
    var loadError by remember(embedUrl, referer) { mutableStateOf<String?>(null) }
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(loadError) { if (loadError != null) retryFocus.requestFocus() }
    val documentInterceptor = remember(embedUrl, referer) {
        PlayerDocumentInterceptor(embedUrl, AppConfig.playerDocumentRules)
    }
    val adFilter = remember(embedUrl, referer) {
        PlayerAdFilter(AppConfig.playerBlockedHosts, AppConfig.playerBlockedUrls)
    }
    val script = remember(embedUrl, referer) { playerScript(AppConfig.playerAdText) }
    val headers = remember(referer) {
        if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()
    }
    val webView = remember(embedUrl, referer) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(true)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
                ): Boolean = false

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                    result.cancel()
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                    result.cancel()
                    return true
                }
            }
            val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (documentStart) WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*"))
            Log.i("PelotaLibre", "Autoplay por frame: $documentStart")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?, request: WebResourceRequest?
                ): WebResourceResponse? {
                    request ?: return null
                    return adFilter.intercept(request.url) ?: documentInterceptor.intercept(request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request ?: return false
                    val uri = request.url
                    if (uri.scheme !in setOf("http", "https") || adFilter.blocks(uri)) return true
                    return request.isForMainFrame && !hostMatches(uri.host, Uri.parse(embedUrl).host)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!active.get()) return
                    view?.requestFocus()
                    if (!documentStart) view?.evaluateJavascript(script, null)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true && active.get()) {
                        loadError = "No se pudo cargar el reproductor. Probá otra señal."
                    }
                    Log.w("PelotaLibre", "Error WebView ${error?.errorCode}: ${request?.url?.host}")
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                    if (request?.isForMainFrame == true && active.get()) {
                        loadError = "El servidor respondió con error ${response?.statusCode}. Probá otra señal."
                    }
                }
            }
            loadUrl(embedUrl, headers)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            active.set(false)
            documentInterceptor.close()
            webView.stopLoading()
            webView.destroy()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { webView })
        loadError?.let { message ->
            Column(
                Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(message, color = Color.White)
                Button(
                    modifier = Modifier.focusRequester(retryFocus),
                    onClick = { loadError = null; webView.loadUrl(embedUrl, headers) }
                ) { Text("Reintentar") }
                Button(onClick = onBack) { Text("Volver a las señales") }
            }
        }
    }
}

private fun hostMatches(target: String?, allowed: String?): Boolean {
    if (target == null || allowed == null) return false
    val allowedHost = allowed.lowercase().removePrefix("www.")
    val targetHost = target.lowercase().removePrefix("www.")
    return targetHost == allowedHost || targetHost.endsWith(".$allowedHost")
}
