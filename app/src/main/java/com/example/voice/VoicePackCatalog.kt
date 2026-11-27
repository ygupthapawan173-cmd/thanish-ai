package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.security.MessageDigest

data class TtsEngineChoice(val id: String, val label: String)
 data class TtsVoiceChoice(val id: String, val locale: String, val network: Boolean, val needsData: Boolean, val quality: Int, val engineRequest: String = "")
object VoicePackCatalog {
    private fun prefs(c: Context) = c.getSharedPreferences("omega_voice_packs", Context.MODE_PRIVATE)
    fun selected(c: Context, language: String): String = prefs(c).getString(language, "").orEmpty()
    fun select(c: Context, language: String, id: String) { prefs(c).edit().putString(language,id).apply() }
    fun engine(c: Context, language: String): String = prefs(c).getString("engine_$language", "").orEmpty()
    fun selectEngine(c: Context, language: String, id: String) {
        if (language !in setOf("en", "te") || (id.isNotEmpty() && engines(c).none { it.id == id })) return
        prefs(c).edit().putString("engine_$language", id).remove(language).apply()
    }
    fun engines(c: Context): List<TtsEngineChoice> = runCatching {
        val pm = c.packageManager
        pm.queryIntentServices(android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { it.serviceInfo?.applicationInfo }
            .map { TtsEngineChoice(it.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.id }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
    fun needsData(v: Voice) = v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
    fun choices(engine: TextToSpeech, requested: String = ""): List<TtsVoiceChoice> = runCatching {
        engine.voices.orEmpty().filter { it.locale.language in setOf("te","en") }.map {
            TtsVoiceChoice(it.name,it.locale.toLanguageTag(),it.isNetworkConnectionRequired,needsData(it),it.quality,requested)
        }.sortedWith(compareBy<TtsVoiceChoice> { it.locale }.thenBy { it.needsData }.thenBy { it.network }.thenByDescending { it.quality }.thenBy { it.id })
    }.getOrDefault(emptyList())
    fun verifyBundled(c: Context, pack: String): String = runCatching {
        require(pack in setOf("model-en-us","model-spk"))
        val lines=c.assets.open("$pack/pack.sha256").bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        require(lines.isNotEmpty())
        val names=lines.map { it.substringAfter("  ") }
        val required=if(pack=="model-spk") listOf("final.ext.raw","mean.vec","transform.mat","mfcc.conf") else listOf("am/final.mdl","conf/model.conf","uuid")
        require(required.all { it in names })
        for(line in lines) {
            val expected=line.substringBefore("  "); val name=line.substringAfter("  ")
            require(expected.matches(Regex("[0-9a-f]{64}")) && !name.startsWith('/') && name.split('/').none { it==".." })
            val digest=MessageDigest.getInstance("SHA-256")
            c.assets.open("$pack/$name").use { input -> val buf=ByteArray(65536); while(true) { val n=input.read(buf); if(n<0) break; digest.update(buf,0,n) } }
            require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == expected)
        }
        "Verified (${lines.size} files)"
    }.getOrElse { "Missing or corrupt — reinstall verified APK" }
}
