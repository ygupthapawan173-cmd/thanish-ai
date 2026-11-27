package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OmegaHorizonOrb(
    orbState: String,
    audioLevel: Float,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(orbState, webViewRef) {
        webViewRef?.evaluateJavascript("window.OmegaBridge && window.OmegaBridge.setState('$orbState');", null)
    }

    LaunchedEffect(audioLevel, webViewRef) {
        val clamped = audioLevel.coerceIn(0f, 1f)
        webViewRef?.evaluateJavascript("window.OmegaBridge && window.OmegaBridge.setLevel($clamped);", null)
    }

    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy(); webViewRef = null }
    }

    AndroidView(
        modifier = modifier.size(size),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                loadUrl("file:///android_asset/omega_horizon/orb.html")
                webViewRef = this
            }
        }
    )
}
