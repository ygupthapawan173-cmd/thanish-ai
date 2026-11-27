package com.example.voice
fun main() {
    var count=0
    fun verify(ok:Boolean) { check(ok); count++ }
    for (fresh in listOf(false,true)) for (enabled in listOf(false,true))
        verify(InstalledSpeechPolicy.paidAllowed(fresh,enabled)==(fresh&&enabled))
    for (paid in listOf(false,true)) for (local in listOf(false,true)) for (locked in listOf(false,true))
        verify(InstalledSpeechPolicy.networkVoiceAllowed(paid,local,locked)==(paid&&!local&&!locked))
    for (lang in listOf("en","te")) for (voice in listOf("en","te","hi")) for (missing in listOf(false,true)) for (network in listOf(false,true)) for (allow in listOf(false,true))
        verify(InstalledSpeechPolicy.voiceEligible(lang,voice,missing,network,allow)==(lang==voice&&!missing&&(!network||allow)))
    verify(InstalledSpeechPolicy.language("Hello Boss") == "en")
    verify(InstalledSpeechPolicy.language("నమస్కారం బాస్") == "te")
    verify(InstalledSpeechPolicy.language("Hello బాస్") == "te")
    println("$count/$count installed speech policy assertions passed")
}
