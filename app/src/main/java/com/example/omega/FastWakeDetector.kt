package com.example.omega

import com.example.voice.AudioCapturePolicy
import com.example.voice.HandsFreeWakePolicy
import com.example.voice.MicLease
import com.example.voice.SpeakerProfilePolicy
import org.json.JSONObject

/**
 * V109 wake coordinator integrated into the active OnlineBackgroundVoiceService.
 * - Uses existing MicLease (VoiceMic.lease) to prevent microphone conflicts.
 * - Guarantees idempotent cleanup: cancel(), shutdown(), closeRecognizer(), and MicLease.release()
 *   each execute at most ONCE per session (no duplicate calls).
 * - Strictly enforces OwnerSpeaker verification without bypassing short samples or partial results.
 */
internal class FastWakeDetector(
    private val lease: MicLease,
    private val leaseOwner: Any,
    private val sensitivityProvider: () -> Float,
    private val ownerFilterEnabled: () -> Boolean = { false },
    private val ownerVerify: (JSONObject) -> Boolean = { true },
    private val minOwnerFrames: () -> Int = { SpeakerProfilePolicy.minFrames }
) {
    interface Session {
        fun cancel()
        fun shutdown()
        fun closeRecognizer()
    }

    @Volatile
    private var activeSession: Session? = null
    private val lock = Any()

    fun bindSession(session: Session) {
        synchronized(lock) {
            activeSession = session
        }
    }

    fun acquireAndBind(session: Session): Boolean {
        synchronized(lock) {
            if (!lease.acquire(leaseOwner)) return false
            activeSession = session
            return true
        }
    }

    /**
     * Atomically detaches the bound wake session, cleans up the speech/recognizer resources
     * exactly once, and releases the microphone lease exactly once if currently owned.
     *
     * @return true if a bound session was present and cleaned up; false if already clean/unbound.
     */
    fun stopAndReleaseMic(
        releaseLease: () -> Unit = { lease.release(leaseOwner) }
    ): Boolean {
        val old: Session?
        val shouldReleaseLease: Boolean
        synchronized(lock) {
            old = activeSession
            activeSession = null
            shouldReleaseLease = lease.owns(leaseOwner)
        }
        if (old != null) {
            runCatching { old.cancel() }
            runCatching { old.shutdown() }
            runCatching { old.closeRecognizer() }
        }
        if (shouldReleaseLease && lease.owns(leaseOwner)) {
            releaseLease()
        }
        return old != null
    }

    fun evaluateResult(hypothesisJson: String, isPartial: Boolean): Boolean {
        val json = runCatching { JSONObject(hypothesisJson) }.getOrNull() ?: return false
        val ownerEnabled = ownerFilterEnabled()
        if (isPartial && ownerEnabled) return false

        val text = if (isPartial) json.optString("partial") else json.optString("text")
        if (text.isBlank()) return false

        val words = json.optJSONArray("result") ?: return false
        val confidence = (0 until words.length()).map {
            words.optJSONObject(it)?.optDouble("conf", Double.NaN) ?: Double.NaN
        }

        val threshold = AudioCapturePolicy.wakeThreshold(sensitivityProvider())
        val spkFrames = json.optInt("spk_frames", 0)
        val verified = if (ownerEnabled) spkFrames >= minOwnerFrames() && ownerVerify(json) else true

        return HandsFreeWakePolicy.acceptWithOwnerGuard(
            text = text,
            confidence = confidence,
            threshold = threshold,
            isPartial = isPartial,
            ownerVerificationEnabled = ownerEnabled,
            spkFrames = spkFrames,
            minFrames = minOwnerFrames(),
            ownerVerified = verified
        )
    }
}
