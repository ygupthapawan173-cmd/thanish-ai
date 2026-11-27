package com.example.voice

/** Strict, deterministic background actions; arbitrary model output is never executed. */
internal object BackgroundVoicePolicy {
    enum class Action { TIME, DATE, BATTERY, TORCH_ON, TORCH_OFF, GREETING, STOP, QUESTION }
    val inlineCommands = listOf("time", "what time is it", "date", "battery", "battery level", "battery percentage", "flashlight on", "flashlight off", "torch on", "torch off", "hello", "stop", "samayam", "torch on chey", "torch off chey")
    fun wakeGrammar(phrases: List<String>): List<String> = phrases + phrases.flatMap { phrase -> inlineCommands.map { "$phrase $it" } } + "[unk]"
    fun matchWake(heard: String, phrases: List<String>): String? = phrases.sortedByDescending { it.length }.firstOrNull { heard == it || heard.startsWith("$it ") }
    fun classify(raw: String): Action {
        val q = raw.lowercase().trim().trimEnd('?', '.', '!').trim().replace(Regex("\\s+"), " ")
        return when (q) {
            "time", "what time is it", "what is the time", "time please", "samayam", "టైం ఎంత", "సమయం ఎంత", "ఇప్పుడు టైం ఎంత" -> Action.TIME
            "date", "what is today's date", "what is the date", "today's date", "తేదీ", "ఈరోజు తేదీ" -> Action.DATE
            "battery", "battery level", "battery percentage", "how much battery is left", "బ్యాటరీ ఎంత", "బ్యాటరీ" -> Action.BATTERY
            "flashlight on", "torch on", "turn on flashlight", "turn on the flashlight", "turn on torch", "torch on chey", "టార్చ్ ఆన్", "టార్చ్ ఆన్ చేయి" -> Action.TORCH_ON
            "flashlight off", "torch off", "turn off flashlight", "turn off the flashlight", "turn off torch", "torch off chey", "టార్చ్ ఆఫ్", "టార్చ్ ఆఫ్ చేయి" -> Action.TORCH_OFF
            "hi", "hello", "hey", "namaste", "ela unnav", "ఎలా ఉన్నావు", "నమస్తే" -> Action.GREETING
            "stop", "cancel", "stop everything", "emergency stop", "cancel all", "అన్నీ ఆపు", "stop listening", "goodbye", "ఆపు", "వద్దు" -> Action.STOP
            else -> Action.QUESTION
        }
    }
    fun canSendCloud(deviceLocked: Boolean, localOnly: Boolean) = VoiceHealthPolicy.canUseCloud(deviceLocked,localOnly,false)
    fun followUp(continuous: Boolean, turns: Int) = continuous && turns < 3
}
