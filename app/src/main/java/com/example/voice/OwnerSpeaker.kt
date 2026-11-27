package com.example.voice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.SpeakerModel
import java.io.File

/** Local, opt-in wake speaker templates. No recordings retained or uploaded. */
internal class OwnerSpeaker(private val context: Context) {
    private val profile = File(context.noBackupFilesDir,"omega-owner-voice-v1.json")
    private val storage = android.util.AtomicFile(profile)
    @Volatile var model: SpeakerModel? = null; private set
    val training = mutableListOf<DoubleArray>()
    var trainingUntil = 0L
    val isTraining get() = trainingUntil > android.os.SystemClock.elapsedRealtime()
    val enrolled get() = profile.exists() || File(profile.path+".bak").exists()
    private val policyPrefs=context.getSharedPreferences("omega_voice_owner_policy",Context.MODE_PRIVATE)
    val enabled get() = enrolled && policyPrefs.getBoolean("filter",false)
    fun toggleFilter() { if(enrolled) policyPrefs.edit().putBoolean("filter",!enabled).apply() }
    @Synchronized fun load() {
        if(model != null) return
        val dest=File(context.noBackupFilesDir,"vosk-spk-0.4").apply { mkdirs() }
        for (name in listOf("mfcc.conf","final.ext.raw","mean.vec","transform.mat")) {
            val f=File(dest,name)
            if(!f.exists() || f.length()==0L) {
                val temp=File(dest,"$name.tmp")
                context.assets.open("model-spk/$name").use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(temp.renameTo(f))
            }
        }
        model=SpeakerModel(dest.absolutePath)
    }
    fun begin() { training.clear(); trainingUntil=android.os.SystemClock.elapsedRealtime()+90_000 }
    fun cancelTraining() { training.clear(); trainingUntil=0 }
    fun clear() { cancelTraining(); storage.delete(); policyPrefs.edit().remove("filter").apply() }
    fun vector(json: JSONObject): DoubleArray? {
        val a=json.optJSONArray("spk") ?: return null
        if(json.optInt("spk_frames",0)<SpeakerProfilePolicy.minFrames || a.length()!=128) return null
        val v=DoubleArray(a.length()) { a.optDouble(it,Double.NaN) }
        return v.takeIf { SpeakerProfilePolicy.similarity(it,it)>0.99 }
    }
    fun enroll(v: DoubleArray): Int {
        if(!isTraining) return 0
        if(training.any { SpeakerProfilePolicy.similarity(it,v)<0.55 }) { training.clear(); return 0 }
        training.add(v)
        val n=training.size
        if(n==SpeakerProfilePolicy.sampleCount) {
            val a=JSONArray(); training.forEach { row -> a.put(JSONArray(row.toList())) }
            val stream=storage.startWrite()
            try { stream.write(a.toString().toByteArray(Charsets.UTF_8)); storage.finishWrite(stream) } catch(e:Exception) { storage.failWrite(stream); throw e }
            cancelTraining()
        }
        return n
    }
    fun verify(json: JSONObject): Boolean {
        if(!enabled) return true
        val v=vector(json) ?: return false
        return runCatching {
            val a=JSONArray(String(storage.readFully(),Charsets.UTF_8)); val samples=(0 until a.length()).map { i ->
                val row=a.getJSONArray(i); DoubleArray(row.length()) { row.getDouble(it) }
            }
            SpeakerProfilePolicy.matches(samples,v)
        }.getOrDefault(false)
    }
    @Synchronized fun close() { cancelTraining(); model?.close(); model=null }
}
