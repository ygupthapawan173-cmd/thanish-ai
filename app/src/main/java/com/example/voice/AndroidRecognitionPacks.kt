package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper

/** Queries/downloads the system provider's real on-device ASR language data; never starts microphone capture. */
class AndroidRecognitionPacks(context: Context) {
    private val app=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer?=null
    private var generation=0L
    private fun intent(tag:String)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,tag)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
    }
    private fun open(): SpeechRecognizer? {
        close()
        if(Build.VERSION.SDK_INT<33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) return null
        return runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(app) }.getOrNull().also { recognizer=it }
    }
    fun check(tag:String, result:(String)->Unit) {
        if(Build.VERSION.SDK_INT<33) { result("Android provider status requires Android 13+"); return }
        val sr=open() ?: run { result("No Android on-device speech service; unlock for online recognition"); return }
        val token=generation
        fun finish(message:String) { if(token==generation) { close(); result(message) } }
        fun matches(tags:List<String>)=tags.any { it.substringBefore('-').equals(tag.substringBefore('-'),true) }
        result("Checking Android speech provider…")
        runCatching { sr.checkRecognitionSupport(intent(tag),app.mainExecutor,object:RecognitionSupportCallback {
            override fun onSupportResult(s:RecognitionSupport) = finish(when {
                matches(s.installedOnDeviceLanguages) -> "Installed (Android provider)"
                matches(s.pendingOnDeviceLanguages) -> "Download pending (Android provider)"
                matches(s.supportedOnDeviceLanguages) -> "Available for download (Android provider)"
                else -> "Offline language not reported by provider; use online recognition"
            })
            override fun onError(error:Int)=finish(if(error==12) "Android provider does not support this language (12). Use online recognition while unlocked." else "Provider status unavailable (error $error)")
        }) }.onFailure { finish("Unable to query Android speech provider") }
        handler.postDelayed({ finish("Provider status check timed out") },6000)
    }
    fun download(tag:String, result:(String)->Unit) {
        if(Build.VERSION.SDK_INT<33) { result("Model download requires Android 13+"); return }
        val sr=open() ?: run { result("No Android on-device speech service"); return }
        val token=generation
        fun finish(message:String) { if(token==generation) { close(); result(message) } }
        result("Requesting Android speech model download…")
        runCatching {
            if(Build.VERSION.SDK_INT>=34) sr.triggerModelDownload(intent(tag),app.mainExecutor,object:ModelDownloadListener {
                override fun onProgress(completedPercent:Int) { if(token==generation) result("Provider download: ${completedPercent.coerceIn(0,100)}%") }
                override fun onSuccess() { if(token==generation) check(tag,result) }
                override fun onScheduled()=finish("Download scheduled by provider; check again later")
                override fun onError(error:Int)=finish(if(error==12) "Android provider does not support this language (12). Use online recognition while unlocked." else "Download failed (provider error $error); use online recognition")
            }) else { sr.triggerModelDownload(intent(tag)); finish("Download requested; check status after completion") }
        }.onFailure { finish("Download request unavailable") }
        handler.postDelayed({ finish("Provider download still unconfirmed; check status again") },180000)
    }
    fun close() { generation++; handler.removeCallbacksAndMessages(null); runCatching { recognizer?.destroy() }; recognizer=null }
}
