package com.example.voice

/** Adaptive noise floor with sustained speech and hangover; PCM frames are still sent for server VAD. */
internal class LiveSilencePolicy {
    private var floor=0.018f
    private var count=0
    private var hang=0
    fun update(level:Float):Boolean {
        val sample = if (level.isFinite()) level.coerceIn(0f,1f) else 0f
        val threshold=(floor*3.0f).coerceIn(0.045f,0.22f)
        if(sample>threshold) { count=(count+1).coerceAtMost(4); if(count>=3) hang=8 }
        else { count=0; if(hang>0) hang-- else floor=(floor*0.96f+sample.coerceIn(0f,0.10f)*0.04f).coerceIn(0.008f,0.08f) }
        return hang>0
    }
}

internal object VoiceHealthPolicy {
    fun stalled(now:Long, frame:Long, nonzero:Long) = now >= 0 && ((frame in 0..now && now-frame>8000) || (nonzero in 0..now && now-nonzero>12000))
    fun commandCaptureAllowed(baseAllowed:Boolean, startedLocked:Boolean, lockedNow:Boolean) = baseAllowed && (startedLocked || !lockedNow)
    fun canUseCloud(locked:Boolean, localOnly:Boolean, muted:Boolean) = !locked && !localOnly && !muted
}
