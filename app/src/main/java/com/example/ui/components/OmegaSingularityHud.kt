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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var selectedImage by remember{mutableStateOf<ByteArray?>(null)}
    var cameraOn by remember{mutableStateOf(false)}
    var cameraConsent by remember{mutableStateOf(false)}
    var gitToken by remember{mutableStateOf("")}
    var replacement by remember{mutableStateOf("")}
    var prUrl by remember{mutableStateOf("")}
    var confirmPublish by remember{mutableStateOf(false)}
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
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        status=if(granted) "Camera permission granted. Reconnect Live, then start camera." else "Camera permission denied"
    }
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null) scope.launch {
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
                require(bytes.size in 1..500000)
                selectedImage=bytes
                "Image prepared locally; reconnect and explicitly send it."
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
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE){engine.disconnect("Paused: session stopped");react.cancel();consent=false;cameraOn=false;cameraConsent=false;gitToken=""}}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);engine.release();manager.release()}
    }
    val currentVerified by rememberUpdatedState(verified)
    DisposableEffect(Unit){onDispose{currentVerified?.file?.delete()}}
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
    Surface(shape=RoundedCornerShape(22.dp),color=Color(0xFF0B1830),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF14294B),Color(0xFF07101F)))).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("OMEGA • 10,000X SINGULARITY",style=MaterialTheme.typography.titleLarge,color=Color(0xFF69F5FF),modifier=Modifier.testTag("singularity_hud"))
            Text(metrics,style=MaterialTheme.typography.bodySmall,color=Color(0xFFB7D7F1))
            Text("Microphone owner: "+owner.name,color=Color(0xFFAD86FF))
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        listOf("Live","ReAct","Evolution").forEachIndexed{i,label->
            OutlinedButton(onClick={if(tab==0 && i!=0){engine.disconnect();cameraOn=false;cameraConsent=false};tab=i},modifier=Modifier.weight(1f).testTag("singularity_tab_"+i),contentPadding=PaddingValues(horizontal=4.dp)){Text(label,maxLines=1)}
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
            OutlinedButton(onClick={cameraOn=false;cameraConsent=false;engine.disconnect()}){Text("Stop Live")}
            OutlinedTextField(prompt,{prompt=it},label={Text("Live text prompt")},modifier=Modifier.fillMaxWidth())
            Button(onClick={engine.sendTextTurn(prompt);prompt=""},enabled=telemetry.connected && prompt.isNotBlank()){Text("Send text")}
            OutlinedButton(onClick={engine.disconnect();consent=false;selectedImage=null;imagePicker.launch("image/*")}){Text("Choose image (stored temporarily)")}
            selectedImage?.let{bytes->
                Text("Selected JPEG: "+bytes.size/1024+" KB · not sent")
                Button(onClick={if(engine.sendVisionFrame(bytes)){selectedImage=null;status="Selected image sent"}else status="Image send failed"},enabled=telemetry.connected){Text("Send selected image to Gemini")}
                OutlinedButton(onClick={selectedImage=null}){Text("Discard selected image")}
            }
            HorizontalDivider()
            Text("Live camera • visible rear-camera preview")
            Row{Checkbox(cameraConsent,{cameraConsent=it;if(!it)cameraOn=false});Text("Send camera frames to Gemini (up to 1 FPS). Do not capture private content or other people without permission.")}
            Button(onClick={
                if(ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){engine.disconnect();cameraPermission.launch(Manifest.permission.CAMERA)}
                else cameraOn=true
            },enabled=telemetry.connected && cameraConsent && !cameraOn){Text("Start camera streaming")}
            OutlinedButton(onClick={cameraOn=false;cameraConsent=false},enabled=cameraOn){Text("Stop camera")}
            if(cameraOn && telemetry.connected && cameraConsent){
                OmegaLiveCameraPreview(onFrame={engine.sendVisionFrame(it)},onFailure={status=it;cameraOn=false})
            }
            LaunchedEffect(telemetry.connected){if(!telemetry.connected){cameraOn=false;cameraConsent=false}}
            Text("No screen recording. Camera and Live stop when leaving the foreground.")
        }
        1->{
            Text("Reviewed phone workflow")
            OutlinedTextField(chain,{chain=it},label={Text("Commands separated by ->")},modifier=Modifier.fillMaxWidth().testTag("singularity_react_input"))
            Text("Example: check battery -> increase volume -> open settings. Unsupported commands rejected; every step needs approval.")
            Button(onClick={react.prepare(chain)},enabled=chain.isNotBlank(),modifier=Modifier.testTag("singularity_review_chain")){Text("Review chain")}
            Text(trace.status,modifier=Modifier.testTag("singularity_react_status"))
            trace.steps.getOrNull(trace.index)?.let{Text("Next: "+it)}
            Button(onClick={scope.launch{react.approveNext()}},enabled=trace.status.startsWith("Awaiting"),modifier=Modifier.testTag("singularity_approve_step")){Text("Approve next step")}
            OutlinedButton(onClick={react.cancel()}){Text("Cancel chain")}
            trace.logs.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
            Text("For screen-node actions use the existing consent-based Phone Agent under Apps. No automatic gestures.")
        }
        2->{
            Text("AI code proposal • review only",modifier=Modifier.testTag("singularity_evolution"))
            OutlinedTextField(path,{path=it},label={Text("Kotlin path")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(source,{source=it.take(64000)},label={Text("Paste current source (remove secrets)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(change,{change=it.take(2000)},label={Text("Requested change")},modifier=Modifier.fillMaxWidth())
            Button(onClick={scope.launch{busy=true;try{draft=SelfEvolutionCompilerLoop.generateDraft(c,path,source,change);status="Draft generated; not compiled or committed"}catch(e:CancellationException){throw e}catch(_:Exception){status="Draft generation failed"}finally{busy=false}}},enabled=!busy && source.length in 1..24000 && change.isNotBlank() && SelfEvolutionCompilerLoop.validPath(path)){Text("Generate review draft (sends source to AI)")}
            if(draft.isNotBlank()){
                androidx.compose.foundation.text.selection.SelectionContainer{Text(draft)}
                Button(onClick={c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,draft),"Share draft"))}){Text("Export draft")}
            }
            HorizontalDivider()
            Text("GitHub draft PR • reviewed source only")
            Text("Paste the exact current source above and a complete reviewed replacement below. Token is used for this request only, never stored.")
            OutlinedTextField(replacement,{replacement=it.take(64000)},label={Text("Reviewed full Kotlin replacement")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
            OutlinedTextField(gitToken,{gitToken=it.take(512)},label={Text("Repository-scoped GitHub token")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
            Button(onClick={confirmPublish=true},enabled=!busy && gitToken.isNotBlank() && OmegaReviewedPrPublisher.validReplacement(path,source,replacement)){Text("Review draft PR submission")}
            if(confirmPublish)AlertDialog(onDismissRequest={confirmPublish=false},title={Text("Create GitHub draft PR?")},text={Text("One replacement: "+path+"\nBase: "+OmegaReviewedPrPublisher.BASE+"\nA new review branch will be created. No merge or installation.")},confirmButton={TextButton(onClick={
                confirmPublish=false
                val token=gitToken;gitToken="";val file=path;val original=source;val next=replacement
                scope.launch{busy=true;try{prUrl=OmegaReviewedPrPublisher.publish(token,file,original,next);status="Draft PR created. Review and run CI in GitHub."}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Draft PR failed"}finally{busy=false}}
            }){Text("Create draft PR")}},dismissButton={TextButton(onClick={confirmPublish=false}){Text("Cancel")}})
            if(prUrl.isNotBlank())OutlinedButton(onClick={c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(prUrl)))}){Text("Open created draft PR")}
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
