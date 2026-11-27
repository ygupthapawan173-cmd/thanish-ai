package com.example.voice
import java.nio.ByteBuffer
import java.nio.ByteOrder
fun main() {
    var n=0
    fun verify(ok:Boolean) { check(ok); n++ }
    verify(GeminiSpeechPolicy.speakers.size==30)
    verify(GeminiSpeechPolicy.speakers.toSet().size==30)
    for (v in GeminiSpeechPolicy.speakers) for (lang in listOf("en","te")) verify(GeminiSpeechPolicy.speaker(v,lang)==v)
    verify(GeminiSpeechPolicy.speaker("unknown","en")=="Charon")
    verify(GeminiSpeechPolicy.speaker("unknown","te")=="Kore")
    for (mask in 0..63) for (length in listOf(0,1,999,1000,1001)) {
        fun bit(i:Int)=(mask and (1 shl i))!=0
        verify(GeminiSpeechPolicy.allowed(bit(0),bit(1),bit(2),bit(3),bit(4),bit(5),length)==(bit(0)&&bit(1)&&!bit(2)&&!bit(3)&&bit(4)&&bit(5)&&length in 1..1000))
    }
    val pcm=byteArrayOf(1,2,3,4)
    val wav=GeminiSpeechPolicy.wav(pcm)
    val b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
    verify(String(wav.copyOfRange(0,4))=="RIFF")
    verify(b.getInt(4)==40)
    verify(String(wav.copyOfRange(8,12))=="WAVE")
    verify(b.getShort(20).toInt()==1)
    verify(b.getShort(22).toInt()==1)
    verify(b.getInt(24)==24000)
    verify(b.getInt(28)==48000)
    verify(b.getShort(34).toInt()==16)
    verify(b.getInt(40)==4)
    verify(wav.copyOfRange(44,48).contentEquals(pcm))
    for (bad in listOf(byteArrayOf(),byteArrayOf(1),ByteArray(GeminiSpeechPolicy.MAX_PCM+2))) verify(runCatching { GeminiSpeechPolicy.wav(bad) }.isFailure)
    println("$n/$n Gemini speech policy and WAV assertions passed")
}
