package com.example.omega

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.sqrt

/** Experimental local wake filter, NOT identity authentication or anti-spoofing. */
object OmegaSpeakerPolicy {
    const val DIMENSION=128
    fun normalize(vector:List<Double>):List<Double>? {
        if(vector.size!=DIMENSION || vector.any{!it.isFinite()})return null
        val norm=sqrt(vector.sumOf{it*it})
        if(!norm.isFinite() || norm<1e-9)return null
        return vector.map{it/norm}
    }
    fun fromResult(json:JSONObject,minFrames:Int):List<Double>? {
        if(json.optInt("spk_frames",0)<minFrames)return null
        val a=json.optJSONArray("spk")?:return null
        return normalize((0 until a.length()).map{a.optDouble(it,Double.NaN)})
    }
    fun centroid(samples:List<List<Double>>):List<Double>? {
        if(samples.size!=3)return null
        val valid=samples.map{normalize(it)?:return null}
        return normalize(List(DIMENSION){i->valid.sumOf{it[i]}/valid.size})
    }
    fun match(reference:List<Double>,sample:List<Double>,minSimilarity:Double=0.65):Boolean {
        val a=normalize(reference)?:return false
        val b=normalize(sample)?:return false
        return a.indices.sumOf{a[it]*b[it]}>=minSimilarity
    }
}

class OmegaSpeakerStore(c:Context) {
    private val prefs=c.applicationContext.getSharedPreferences("omega_local_speaker_v1",Context.MODE_PRIVATE)
    companion object {private const val ALIAS="omega_local_speaker_aes_v1";private val lock=Any()}
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(ALIAS,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun exists()=prefs.contains("template")
    fun save(vector:List<Double>)=synchronized(lock){
        val normalized=requireNotNull(OmegaSpeakerPolicy.normalize(vector))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=cipher.doFinal(JSONArray(normalized).toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("template",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())
    }
    fun load():List<Double>?=synchronized(lock){
        val raw=prefs.getString("template",null)?:return@synchronized null
        val parts=raw.split('.',limit=2);require(parts.size==2)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
        val a=JSONArray(String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8))
        OmegaSpeakerPolicy.normalize((0 until a.length()).map{a.optDouble(it,Double.NaN)})
    }
    fun delete()=synchronized(lock){
        check(prefs.edit().remove("template").commit())
        KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry(ALIAS)
    }
}
