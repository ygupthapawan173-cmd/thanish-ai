package com.example.voice

internal object VoicePackPolicy {
    fun assetFor(locale: String): String = if(locale.substringBefore('-').equals("te",true)) "model-te-in" else "model-en-us"
    fun localeFor(asset: String): String = if(asset == "model-te-in") "te-IN" else "en-US"
    fun eligible(code: Int): Boolean = code in setOf(1,2,3,4,5,6,7,8,10,11,12,13,14,15)
    fun allowNetworkVoice(localOnly: Boolean, locked: Boolean) = !localOnly && !locked
}
