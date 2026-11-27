package com.example.voice

/** Fallback stays local when privacy requires it; English Vosk cannot transcribe Telugu. */
internal object EliteRoutingPolicy {
    fun allowVosk(locale: String, code: Int): Boolean = locale != "te-IN" && code in setOf(1,2,3,4,5,6,7,8,10,11,12,13,14,15)
    fun allowOnline(localOnly: Boolean) = !localOnly
    fun enableSwitch(installed: List<String>, online: List<String>, local: Boolean): Boolean {
        val available = if(local) installed else installed + online
        return listOf("te", "en").all { prefix -> available.any { it.substringBefore('-').equals(prefix,true) } }
    }
    fun silenceFault(zeroFrames: Int) = zeroFrames >= 50
    fun shouldAcknowledge(autoSpeak: Boolean, inline: String) = autoSpeak && inline.isBlank()
}
