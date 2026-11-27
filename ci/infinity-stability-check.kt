package com.example.ai
import com.example.voice.VoiceHealthPolicy
fun main() {
    val results = listOf(
        "free blocks Gemini" to !SmartModelPolicy.allowsDirectGemini(SmartModelPolicy.Mode.FREE),
        "manual permits Gemini" to SmartModelPolicy.allowsDirectGemini(SmartModelPolicy.Mode.MANUAL),
        "smart permits Gemini" to SmartModelPolicy.allowsDirectGemini(SmartModelPolicy.Mode.SMART),
        "active unlocked capture" to VoiceHealthPolicy.commandCaptureAllowed(true,false,false),
        "lock transition aborts" to !VoiceHealthPolicy.commandCaptureAllowed(true,false,true),
        "locked offline capture allowed" to VoiceHealthPolicy.commandCaptureAllowed(true,true,true),
        "privacy mute blocks locked capture" to !VoiceHealthPolicy.commandCaptureAllowed(false,true,true),
        "permission loss blocks capture" to !VoiceHealthPolicy.commandCaptureAllowed(false,false,false)
    )
    results.forEach { (name,pass) -> check(pass) { name }; println("PASS: $name") }
    println("${results.size}/${results.size} stability policy checks passed")
}
