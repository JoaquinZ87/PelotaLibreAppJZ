package com.jz.pelotalibretv.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Message
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
import java.util.concurrent.atomic.AtomicBoolean

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
    val active = remember { AtomicBoolean(true) } // corta el loop de autoplay al salir del player

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
                    view.requestFocus()
                    // Loop persistente (solo-JS): cada ~2s entra a los iframes same-origin, hace play()
                    // + desmutea (si se frenó, lo re-arranca), clickea el botón de play del propio
                    // player, y ESCONDE overlays de ads. No toca la pantalla (no clickea ads).
                    tick(view, active)
                }
            }

            val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()
            loadUrl(embedUrl, headers)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
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

/**
 * Loop persistente SOLO-JS (cada ~2s hasta salir del player): corre [TICK_JS], que recorre el
 * documento y sus iframes same-origin para (1) desmutear + `play()` cada `<video>` (si se frenó, lo
 * re-arranca), (2) clickear el botón de play del propio player si nada reproduce, y (3) ESCONDER
 * overlays de ads (divs fijos de z-index alto sin video/iframe adentro). NO toca la pantalla, así
 * nunca clickea un anuncio. Se corta cuando [active] pasa a false (al salir del reproductor).
 */
private fun tick(webView: WebView, active: AtomicBoolean) {
    if (!active.get()) return
    webView.evaluateJavascript(TICK_JS, null)
    webView.postDelayed({ tick(webView, active) }, 2000)
}

private const val TICK_JS = """
(function(){
  function walk(doc){
    try{
      var vs=doc.getElementsByTagName('video');
      var playing=false;
      for(var i=0;i<vs.length;i++){var v=vs[i];
        try{v.muted=false;v.volume=1;}catch(e){}
        if(!v.paused && !v.ended && v.readyState>2){playing=true;} else {try{v.play();}catch(e){}}
      }
      if(vs.length && !playing){
        var sels=['.play-wrapper','.vjs-big-play-button','.jw-icon-display','.jw-display-icon-container','[data-player]','.clappr-player','.player-poster','.poster'];
        for(var s=0;s<sels.length;s++){var el=doc.querySelector(sels[s]); if(el){try{el.click();}catch(e){}}}
      }
      // Esconder overlays de ads. Protege al PLAYER: ancestros de un <video> y todo lo que SEA o
      // CONTENGA media grande (iframe/video >= 50% x 40% del viewport). Esconde lo demás que sea
      // position:fixed (o absolute con z alto): los ads modales/banners/age-gate casi siempre son fixed.
      var vw=(doc.documentElement&&doc.documentElement.clientWidth)||1;
      var vh=(doc.documentElement&&doc.documentElement.clientHeight)||1;
      var prot=[]; for(var pv=0;pv<vs.length;pv++){var n=vs[pv]; while(n){prot.push(n); n=n.parentElement;}}
      function esProt(e){for(var p=0;p<prot.length;p++){if(prot[p]===e)return true;}return false;}
      function esGrande(e){return e.offsetWidth>=vw*0.5 && e.offsetHeight>=vh*0.4;}
      function contieneMediaGrande(e){var m=e.querySelectorAll?e.querySelectorAll('iframe,video'):[];for(var q=0;q<m.length;q++){if(esGrande(m[q]))return true;}return false;}
      var els=doc.body?doc.body.querySelectorAll('*'):[];
      for(var k=0;k<els.length;k++){var e=els[k];
        try{
          if(esProt(e)) continue;
          var st=doc.defaultView.getComputedStyle(e);
          var fixed=st.position==='fixed';
          var absAlto=st.position==='absolute' && (parseInt(st.zIndex)||0)>=1000;
          if(!fixed && !absAlto) continue;
          if(e.offsetWidth<60 || e.offsetHeight<30) continue;
          if((e.tagName==='IFRAME'||e.tagName==='VIDEO') && esGrande(e)) continue; // es el player
          if(contieneMediaGrande(e)) continue;                                     // contiene el player
          e.style.setProperty('display','none','important');
        }catch(e2){}
      }
      var ifr=doc.getElementsByTagName('iframe');
      for(var j=0;j<ifr.length;j++){try{var d=ifr[j].contentDocument||(ifr[j].contentWindow&&ifr[j].contentWindow.document); if(d)walk(d);}catch(e){}}
    }catch(e){}
  }
  walk(document);
})();
"""
