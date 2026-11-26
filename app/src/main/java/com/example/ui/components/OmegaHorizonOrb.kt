package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.JarvisStatus

/** Bundled MIT Horizon visual renderer; no remote URLs, JS interfaces, mic access or new dependencies. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OmegaHorizonOrb(status:JarvisStatus,level:Float,onClick:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var failed by remember{mutableStateOf(false)}
    var ready by remember{mutableStateOf(false)}
    var rendererGone by remember{mutableStateOf(false)}
    val html=remember(context){
        runCatching{
            val source=context.assets.open("omega_horizon/jarvis-orb.js").bufferedReader().use{it.readText()}
            """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'none'; img-src 'none'; media-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'">
            <style>html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:transparent}canvas{display:block;width:100%;height:100%;pointer-events:none}</style>
            </head><body><canvas id="orb"></canvas><script>
            $source
            window.omegaReady=false;
            try{
              window.omegaOrb=JarvisOrb.create(document.getElementById('orb'),{quality:'auto',state:'idle'});
              window.omegaReady=!!window.omegaOrb.hasFrameClock;
            }catch(e){window.omegaReady=false;}
            </script></body></html>
            """.trimIndent()
        }.getOrNull()
    }
    if(failed || html==null){
        OmegaUnifiedGpuOrb(status,level,onClick,modifier)
        return
    }
    val view=remember(context){
        runCatching{
            WebView(context).apply{
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled=false;isHorizontalScrollBarEnabled=false
                isFocusable=false;isClickable=false
                importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                settings.apply{
                    javaScriptEnabled=true
                    blockNetworkLoads=true
                    allowFileAccess=false
                    allowContentAccess=false
                    domStorageEnabled=false
                    javaScriptCanOpenWindowsAutomatically=false
                    setSupportMultipleWindows(false)
                    mediaPlaybackRequiresUserGesture=true
                }
                webViewClient=object:WebViewClient(){
                    override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true
                    override fun onPageFinished(view:WebView?,url:String?){
                        view?.evaluateJavascript("window.omegaReady===true"){value->
                            if(value=="true")ready=true else failed=true
                        }
                    }
                    override fun onRenderProcessGone(view:WebView?,detail:RenderProcessGoneDetail?):Boolean{
                        rendererGone=true;failed=true
                        return true
                    }
                }
                // Parent Box owns the existing Compose tap action. Web renderer has no controls.
                setOnTouchListener { _, _ -> false }
                loadDataWithBaseURL("https://omega.invalid/",html,"text/html","UTF-8",null)
            }
        }.getOrNull()
    }
    if(view==null){OmegaUnifiedGpuOrb(status,level,onClick,modifier);return}
    DisposableEffect(view,lifecycle){
        val observer=LifecycleEventObserver{_,event->
            if(!rendererGone){
                when(event){
                    Lifecycle.Event.ON_PAUSE->{view.evaluateJavascript("window.omegaOrb&&window.omegaOrb.setPaused(true)",null);view.onPause()}
                    Lifecycle.Event.ON_RESUME->{view.onResume();view.evaluateJavascript("window.omegaOrb&&window.omegaOrb.setPaused(false)",null)}
                    else->Unit
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose{
            lifecycle.removeObserver(observer)
            if(!rendererGone)runCatching{view.stopLoading();view.onPause()}
            runCatching{view.destroy()}
        }
    }
    LaunchedEffect(ready,status,level){
        if(ready && !rendererGone){
            val mode=when(status){
                JarvisStatus.LISTENING->"listening"
                JarvisStatus.THINKING->"thinking"
                JarvisStatus.SPEAKING->"speaking"
                JarvisStatus.ALERT->"alert"
                JarvisStatus.ONLINE->"idle"
            }
            val amplitude=if(level.isFinite())level.coerceIn(0f,1f) else 0f
            view.evaluateJavascript("window.omegaOrb.setState('$mode');window.omegaOrb.setLevel($amplitude);",null)
        }
    }
    LaunchedEffect(view){
        kotlinx.coroutines.delay(5000)
        if(!ready)failed=true
    }
    Box(modifier.size(252.dp).clickable(onClick=onClick),contentAlignment=Alignment.Center){
        if(!ready)OmegaSafeSingularityVisual(status,level)
        AndroidView(factory={view},modifier=Modifier.size(252.dp))
        // Transparent Compose hit surface above WebView keeps one native accessible tap action.
        Box(Modifier.matchParentSize().clickable(onClick=onClick))
    }
}
