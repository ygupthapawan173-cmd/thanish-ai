package com.example.ui.components

import android.content.Context
import android.app.ActivityManager
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.JarvisStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Depth-tested OpenGL ES sphere with real mesh normals; existing Canvas fallback. */
@Composable
fun OmegaUnifiedGpuOrb(status: JarvisStatus, amplitude: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember { mutableStateOf(
        context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion < 0x20000
    ) }
    if (failed) {
        OmegaSafeSingularityVisual(status, amplitude, modifier.clickable(onClick = onClick))
        return
    }
    val view = remember(context) { OmegaGpuOrbView(context) }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.onPause() }
    }
    Box(modifier.size(216.dp).clickable(onClick = onClick)) {
        AndroidView(factory = { view }, modifier = Modifier.size(216.dp), update = {
            it.failure = { failed = true }
            it.scene.status = status
            it.scene.amplitude = amplitude.coerceIn(0f,1f)
            it.setOnClickListener { onClick() }
            it.contentDescription = "Interactive 3D assistant orb"
        })
    }
}

private class OmegaGpuOrbView(context: Context) : GLSurfaceView(context) {
    var failure: (() -> Unit)? = null
    val scene = OmegaOrbScene { post { failure?.invoke() } }
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8,8,8,0,16,0)
        preserveEGLContextOnPause = true
        setRenderer(scene)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

private class OmegaOrbScene(private val onFailure: () -> Unit) : GLSurfaceView.Renderer {
    @Volatile var status = JarvisStatus.ONLINE
    @Volatile var amplitude = 0f
    private var program=0
    private var position=0
    private var mvpHandle=0
    private var modelHandle=0
    private var tintHandle=0
    private var wireHandle=0
    private val projection=FloatArray(16)
    private val view=FloatArray(16)
    private val model=FloatArray(16)
    private val pv=FloatArray(16)
    private val mvp=FloatArray(16)
    private val sphere=mesh()
    private val grid=lattice()
    private var initialized=false
    private fun point(lat:Double,lon:Double)=floatArrayOf(
        (cos(lat)*cos(lon)).toFloat(),sin(lat).toFloat(),(cos(lat)*sin(lon)).toFloat())
    private fun buffer(values:List<Float>):FloatBuffer=ByteBuffer.allocateDirect(values.size*4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values.toFloatArray()); position(0) }
    private fun mesh():FloatBuffer {
        val values=ArrayList<Float>()
        for(i in 0 until 28) for(j in 0 until 44) {
            val a=point(-PI/2+PI*i/28,2*PI*j/44)
            val b=point(-PI/2+PI*(i+1)/28,2*PI*j/44)
            val c=point(-PI/2+PI*(i+1)/28,2*PI*(j+1)/44)
            val d=point(-PI/2+PI*i/28,2*PI*(j+1)/44)
            for(v in listOf(a,b,c,a,c,d)) v.forEach { values.add(it) }
        }
        return buffer(values)
    }
    private fun lattice():FloatBuffer {
        val values=ArrayList<Float>()
        fun line(a:FloatArray,b:FloatArray) { a.forEach { values.add(it*1.006f) }; b.forEach { values.add(it*1.006f) } }
        for(i in 1..10) for(j in 0 until 56)
            line(point(-PI/2+PI*i/11,2*PI*j/56),point(-PI/2+PI*i/11,2*PI*(j+1)/56))
        for(j in 0 until 16) for(i in 0 until 36)
            line(point(-PI/2+PI*i/36,2*PI*j/16),point(-PI/2+PI*(i+1)/36,2*PI*j/16))
        return buffer(values)
    }
    private fun shader(kind:Int,source:String):Int {
        val id=glCreateShader(kind)
        glShaderSource(id,source); glCompileShader(id)
        val result=IntArray(1); glGetShaderiv(id,GL_COMPILE_STATUS,result,0)
        check(result[0]==GL_TRUE) { glGetShaderInfoLog(id) }
        return id
    }
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        try {
            val v=shader(GL_VERTEX_SHADER, """
                attribute vec3 aPos;
                uniform mat4 uMvp;
                uniform mat4 uModel;
                varying vec3 n;
                void main(){ n=normalize((uModel*vec4(aPos,0.0)).xyz); gl_Position=uMvp*vec4(aPos,1.0); }
            """.trimIndent())
            val f=shader(GL_FRAGMENT_SHADER, """
                precision mediump float;
                varying vec3 n;
                uniform vec3 uTint;
                uniform float uWire;
                void main(){ vec3 normal=normalize(n);
                    float diffuse=max(dot(normal,normalize(vec3(0.35,0.65,1.0))),0.0);
                    float rim=pow(1.0-abs(normal.z),2.0);
                    vec3 base=uTint*(0.20+diffuse*0.62)+vec3(0.08,0.25,0.45)*rim;
                    gl_FragColor=vec4(mix(base,uTint*0.85+vec3(0.12,0.24,0.3),uWire),1.0); }
            """.trimIndent())
            program=glCreateProgram(); glAttachShader(program,v); glAttachShader(program,f); glLinkProgram(program)
            val linked=IntArray(1); glGetProgramiv(program,GL_LINK_STATUS,linked,0)
            check(linked[0]==GL_TRUE) { glGetProgramInfoLog(program) }
            glDeleteShader(v); glDeleteShader(f)
            position=glGetAttribLocation(program,"aPos"); mvpHandle=glGetUniformLocation(program,"uMvp")
            modelHandle=glGetUniformLocation(program,"uModel"); tintHandle=glGetUniformLocation(program,"uTint")
            wireHandle=glGetUniformLocation(program,"uWire")
            glClearColor(0.0196f,0.0431f,0.102f,1f); glEnable(GL_DEPTH_TEST)
            Matrix.setLookAtM(view,0,0f,0f,4.1f,0f,0f,0f,0f,1f,0f)
            initialized=true
        } catch (_:Exception) { initialized=false; onFailure() }
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int) {
        glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,39f,width.coerceAtLeast(1).toFloat()/height.coerceAtLeast(1),0.1f,20f)
    }
    private fun draw(buffer:FloatBuffer,mode:Int,wire:Float) {
        buffer.position(0); glVertexAttribPointer(position,3,GL_FLOAT,false,0,buffer)
        glUniform1f(wireHandle,wire); glDrawArrays(mode,0,buffer.capacity()/3)
    }
    override fun onDrawFrame(gl:GL10?) {
        if(!initialized) return
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val t=(SystemClock.elapsedRealtime()%360000L)/1000f
        val pulse=1f+0.025f*sin(t*2f)+if(status==JarvisStatus.LISTENING) amplitude*0.10f else 0f
        Matrix.setIdentityM(model,0); Matrix.rotateM(model,0,t*19f,0.3f,1f,0.25f); Matrix.scaleM(model,0,pulse,pulse,pulse)
        Matrix.multiplyMM(pv,0,projection,0,view,0); Matrix.multiplyMM(mvp,0,pv,0,model,0)
        glUseProgram(program); glUniformMatrix4fv(mvpHandle,1,false,mvp,0); glUniformMatrix4fv(modelHandle,1,false,model,0)
        val color=when(status) {
            JarvisStatus.LISTENING -> floatArrayOf(0.18f,0.95f,1f)
            JarvisStatus.THINKING -> floatArrayOf(0.55f,0.32f,1f)
            JarvisStatus.SPEAKING -> floatArrayOf(0.28f,0.65f,1f)
            JarvisStatus.ALERT -> floatArrayOf(0.95f,0.45f,0.35f)
            else -> floatArrayOf(0.12f,0.58f,0.94f)
        }
        glUniform3fv(tintHandle,1,color,0); glEnableVertexAttribArray(position)
        draw(sphere,GL_TRIANGLES,0f); glLineWidth(1f); draw(grid,GL_LINES,1f)
        glDisableVertexAttribArray(position)
    }
}
