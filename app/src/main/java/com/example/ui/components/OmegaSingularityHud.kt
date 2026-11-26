package com.example.ui.components

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ai.*
import com.example.control.*
import com.example.omega.*
import com.example.ui.JarvisStatus
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.math.*

/** Integrated Singularity dashboard: each action calls an actual implementation. */
@Composable
fun OmegaSingularityHudScreen() {
    val c=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope()
    val engine=remember{GeminiLiveDuplexEngine(c.applicationContext)}
    val manager=remember{PhoneControlManager(c.applicationContext)}
    val react=remember{AutonomousReActOrchestrator(manager)}
    val telemetry by engine.telemetry.collectAsState()
    val trace by react.trace.collectAsState()
    val owner by OmegaAssistantCore.sessions.owner.collectAsState()
    var tab by remember{mutableIntStateOf(0)}
    var consent by remember{mutableStateOf(false)}
    var model by remember{mutableStateOf("")}
    var prompt by remember{mutableStateOf("")}
    var chain by remember{mutableStateOf("")}
    var path by remember{mutableStateOf("app/src/main/java/com/example/")}
    var source by remember{mutableStateOf("")}
    var change by remember{mutableStateOf("")}
    var draft by remember{mutableStateOf("")}
    var hash by remember{mutableStateOf("")}
    var verified by remember{mutableStateOf<SelfEvolutionCompilerLoop.VerifiedUpdate?>(null)}
    var status by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var metrics by remember{mutableStateOf("")}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        status=if(granted) "Permission granted. Tap Connect again." else "Microphone denied"
    }
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null && telemetry.connected) scope.launch {
            status=try {
                val bytes=withContext(Dispatchers.IO){
                    val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    c.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
                    require(options.outWidth>0 && options.outHeight>0)
                    var sample=1
                    while(max(options.outWidth,options.outHeight)/sample>1024)sample*=2
                    val bitmap=c.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("Image unavailable")
                    try{ByteArrayOutputStream().use{out->check(bitmap.compress(Bitmap.CompressFormat.JPEG,70,out));out.toByteArray()}}finally{bitmap.recycle()}
                }
                if(engine.sendVisionFrame(bytes)) "Selected image sent" else "Image not sent"
            }catch(e:CancellationException){throw e}catch(_:Exception){"Image could not be sent"}
        }
    }
    val apkPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null && !busy) scope.launch {
            busy=true;verified?.file?.delete();verified=null
            try { verified=SelfEvolutionCompilerLoop.verifyUpdate(c,uri,hash.trim());status="APK hash, package, signing certificate and version checked. Review before install." }
            catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Verification failed"}finally{busy=false}
        }
    }
    DisposableEffect(lifecycle,engine,manager){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE){engine.disconnect("Paused: session stopped");react.cancel();consent=false}}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);engine.release();manager.release()}
    }
    DisposableEffect(Unit){onDispose{verified?.file?.delete()}}
    LaunchedEffect(Unit){
        while(true){
            val m=ActivityManager.MemoryInfo().also{c.getSystemService(ActivityManager::class.java).getMemoryInfo(it)}
            val b=c.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val thermal=if(Build.VERSION.SDK_INT>=29) when(c.getSystemService(PowerManager::class.java).currentThermalStatus){
                0->"Normal";1->"Light";2->"Moderate";3->"Severe";4->"Critical";5->"Emergency";6->"Shutdown";else->"Unknown"
            }else "Unavailable"
            metrics="RAM free "+m.availMem/1048576+" MB · Battery "+(if(b in 0..100) "$b%" else "Unavailable")+" · Thermal "+thermal
            delay(5000)
        }
    }
    Text("OMEGA • 10,000X SINGULARITY",style=MaterialTheme.typography.titleLarge,color=Color(0xFF69F5FF),modifier=Modifier.testTag("singularity_hud"))
    Text(metrics,style=MaterialTheme.typography.bodySmall)
    Text("Microphone owner: "+owner.name)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        listOf("Live","ReAct","Evolution").forEachIndexed{i,label->
            OutlinedButton(onClick={if(tab==0 && i!=0)engine.disconnect();tab=i},modifier=Modifier.weight(1f).testTag("singularity_tab_"+i),contentPadding=PaddingValues(horizontal=4.dp)){Text(label,maxLines=1)}
        }
    }
    when(tab){
        0->{
            val orbState=if(telemetry.speakerAmplitude>0.01f) JarvisStatus.SPEAKING else if(telemetry.connected) JarvisStatus.LISTENING else JarvisStatus.ONLINE
            OmegaSafeSingularityVisual(orbState,max(telemetry.micAmplitude,telemetry.speakerAmplitude))
            Canvas(Modifier.fillMaxWidth().height(44.dp)){
                val amp=max(telemetry.micAmplitude,telemetry.speakerAmplitude).coerceIn(0f,1f)
                for(i in 0 until 24){val x=(i+0.5f)*size.width/24;val h=(2f+abs(sin(i*0.7f))*amp*size.height/2);drawLine(Color(0xFF69F5FF),Offset(x,size.height/2-h),Offset(x,size.height/2+h),strokeWidth=3f)}
            }
            Text(telemetry.stateLabel);Text(telemetry.lastTranscript)
            OutlinedTextField(model,{model=it},label={Text("Gemini Live model ID")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!telemetry.connected && owner!=MicOwner.LIVE)
            Row{Checkbox(consent,{consent=it;if(!it)engine.disconnect()});Text("I agree to send microphone audio to Gemini during this foreground session. Charges may apply.")}
            Button(onClick={
                if(ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.RECORD_AUDIO)
                else engine.connect(model.trim())
            },enabled=consent && owner==MicOwner.NONE && model.isNotBlank(),modifier=Modifier.testTag("singularity_connect")){Text("Connect Live")}
            OutlinedButton(onClick={engine.disconnect()}){Text("Stop Live")}
            OutlinedTextField(prompt,{prompt=it},label={Text("Live text prompt")},modifier=Modifier.fillMaxWidth())
            Button(onClick={engine.sendTextTurn(prompt);prompt=""},enabled=telemetry.connected && prompt.isNotBlank()){Text("Send text")}
            OutlinedButton(onClick={imagePicker.launch("image/*")},enabled=telemetry.connected){Text("Choose image to send to Gemini")}
            Text("No screen recording. Image picker sends only the selected image. Live stops when the app loses focus.")
        }
        1->{
            Text("Reviewed phone workflow")
            OutlinedTextField(chain,{chain=it},label={Text("Commands separated by ->")},modifier=Modifier.fillMaxWidth())
            Text("Example: check battery -> increase volume -> open settings. Unsupported commands rejected; every step needs approval.")
            Button(onClick={react.prepare(chain)},enabled=chain.isNotBlank()){Text("Review chain")}
            Text(trace.status)
            trace.steps.getOrNull(trace.index)?.let{Text("Next: "+it)}
            Button(onClick={react.approveNext()},enabled=trace.status.startsWith("Awaiting")){Text("Approve next step")}
            OutlinedButton(onClick={react.cancel()}){Text("Cancel chain")}
            trace.logs.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
            Text("For screen-node actions use the existing consent-based Phone Agent under Apps. No automatic gestures.")
        }
        2->{
            Text("AI code proposal • review only")
            OutlinedTextField(path,{path=it},label={Text("Kotlin path")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(source,{source=it.take(24000)},label={Text("Paste current source (remove secrets)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(change,{change=it.take(2000)},label={Text("Requested change")},modifier=Modifier.fillMaxWidth())
            Button(onClick={scope.launch{busy=true;try{draft=SelfEvolutionCompilerLoop.generateDraft(c,path,source,change);status="Draft generated; not compiled or committed"}catch(e:CancellationException){throw e}catch(_:Exception){status="Draft generation failed"}finally{busy=false}}},enabled=!busy && source.isNotBlank() && change.isNotBlank() && SelfEvolutionCompilerLoop.validPath(path)){Text("Generate review draft (sends source to AI)")}
            if(draft.isNotBlank()){
                androidx.compose.foundation.text.selection.SelectionContainer{Text(draft)}
                Button(onClick={c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,draft),"Share draft"))}){Text("Export draft")}
            }
            OutlinedButton(onClick={c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/ygupthapawan173-cmd/thanish-ai/pulls")))}){Text("Open GitHub PR review")}
            HorizontalDivider()
            Text("Verified update • choose an APK and independently trusted release SHA-256")
            OutlinedTextField(hash,{hash=it;verified?.file?.delete();verified=null},label={Text("Expected SHA-256")},modifier=Modifier.fillMaxWidth())
            Button(onClick={apkPicker.launch(arrayOf("application/vnd.android.package-archive","application/octet-stream"))},enabled=!busy && SelfEvolutionCompilerLoop.validDigest(hash.trim())){Text("Select & verify APK")}
            verified?.let{v->
                Text("Verified candidate version "+v.version+" · "+v.digest.take(16))
                Button(onClick={
                    if(Build.VERSION.SDK_INT>=26 && !c.packageManager.canRequestPackageInstalls()){
                        c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+c.packageName)))
                        status="Allow this source only if trusted, then return and tap Install."
                    }else scope.launch{
                        try{val intent=withContext(Dispatchers.IO){SelfEvolutionCompilerLoop.installIntent(c,v)};c.startActivity(intent)}
                        catch(e:Exception){status=e.message?:"Installer unavailable"}
                    }
                }){Text("Open Android installer")}
            }
            Text("No automatic commits or installs. Different package, signer, hash or non-newer version is rejected.")
        }
    }
    if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall)
}
