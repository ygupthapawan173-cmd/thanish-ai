package com.example.ui.components
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Visible rear-camera preview, at most one JPEG per second. No recording to disk. */
@Composable
fun OmegaLiveCameraPreview(onFrame:(ByteArray)->Boolean,onFailure:(String)->Unit) {
    val c=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val view=remember(c){PreviewView(c).apply{implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
    val send by rememberUpdatedState(onFrame)
    val fail by rememberUpdatedState(onFailure)
    var count by remember{mutableIntStateOf(0)}
    var status by remember{mutableStateOf("Starting camera")}
    DisposableEffect(owner,view) {
        val active=AtomicBoolean(true)
        val executor=Executors.newSingleThreadExecutor()
        val main=ContextCompat.getMainExecutor(c)
        val future=ProcessCameraProvider.getInstance(c)
        var provider:ProcessCameraProvider?=null
        val preview=Preview.Builder().build()
        val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var last=0L
        analysis.setAnalyzer(executor){image->
            try {
                val now=SystemClock.elapsedRealtime()
                if(active.get() && now-last>=1000L) {
                    last=now
                    val bitmap=image.toBitmap()
                    val ratio=minOf(1f,768f/maxOf(bitmap.width,bitmap.height))
                    val scaled=if(ratio<1f) Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true) else bitmap
                    val rotated=if(image.imageInfo.rotationDegrees!=0) Bitmap.createBitmap(scaled,0,0,scaled.width,scaled.height,Matrix().apply{postRotate(image.imageInfo.rotationDegrees.toFloat())},true) else scaled
                    val jpeg=try{ByteArrayOutputStream().use{out->check(rotated.compress(Bitmap.CompressFormat.JPEG,65,out));out.toByteArray()}}
                    finally{if(rotated!==scaled)rotated.recycle();if(scaled!==bitmap)scaled.recycle();bitmap.recycle()}
                    main.execute{if(active.get()){
                        if(jpeg.size in 1..500000 && send(jpeg)){count++;status="Streaming JPEG · "+count+" frames sent"}else status="Frame not sent — connection unavailable or busy"
                    }}
                }
            }catch(_:Exception){main.execute{if(active.get())status="Camera frame conversion failed"}}
            finally{image.close()}
        }
        future.addListener({
            if(active.get())try{
                provider=future.get()
                preview.setSurfaceProvider(view.surfaceProvider)
                provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
                status="Camera active · up to 1 FPS"
            }catch(_:Exception){fail("Camera unavailable or permission denied")}
        },main)
        onDispose{
            active.set(false);analysis.clearAnalyzer()
            runCatching{provider?.unbind(preview,analysis)}
            executor.shutdownNow()
        }
    }
    AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(190.dp))
    Text(status,style=MaterialTheme.typography.bodySmall)
}
