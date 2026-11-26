package com.example.omega

enum class OmegaLiveRoute(val label:String){SYSTEM("System / connected headset"),SPEAKER("Phone speaker"),HEADSET("Prefer Bluetooth SCO / LE or wired headset")}
enum class OmegaWakeConfidence(val label:String,val minimum:Double){FAST("Responsive (0.55)",0.55),STANDARD("Balanced (0.65)",0.65),HIGH("High confidence (0.80)",0.80)}
object OmegaEliteConversation {
    const val MAX_PENDING=3
    fun recoveryDelay(attempt:Int):Long? = when(attempt){1->350L;2->1500L;else->null}
    fun resetRecoveryBudget(stableMs:Long):Boolean=stableMs>=60000L
    fun appendPending(queue:List<String>,text:String):List<String>? = if(queue.size>=MAX_PENDING || text.isBlank())null else queue+text.take(2000)
    fun removePending(queue:List<String>,expected:String):List<String> = if(queue.firstOrNull()==expected)queue.drop(1) else queue
    fun isEndPhrase(text:String):Boolean = OmegaPolicy.normalize(text) in setOf("stop listening","end conversation","go to sleep","goodbye jarvis","stop jarvis","conversation over","ఆపు","వినడం ఆపు","చాలు జార్విస్")
    fun retryLive(reason:String,retries:Int):Boolean = retries<1 &&
        (reason.startsWith("Live connection failed") && !reason.contains("HTTP 4") || reason=="Setup timed out" || reason=="Network too slow" || reason=="Microphone read failed" || reason=="Playback failed" || reason=="Audio pipeline stalled" || reason=="Microphone or playback unavailable")
    fun canFollowup(enabled:Boolean,turns:Int,pending:String)=enabled && turns<3 && pending.isBlank()
    fun confidenceAccepted(confidences:List<Double>,expectedWords:Int,threshold:Double=0.65):Boolean =
        expectedWords>0 && confidences.size==expectedWords && confidences.all{it.isFinite() && it in threshold..1.0}
}
