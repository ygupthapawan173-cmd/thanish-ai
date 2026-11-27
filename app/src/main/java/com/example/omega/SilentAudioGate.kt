package com.example.omega

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.voice.BluetoothAudioRouter
import com.example.voice.AudioCapturePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.example.voice.VoiceMic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * High-sensitivity local Voice Activity Detector (VAD) using AudioSource.MIC
 * so far-field "Hey Jarvis" speech triggers reliably without attenuation.
 */
class SilentAudioGate(
    private val thresholdProvider: () -> Double = { 550.0 },
    private val requiredFrames: Int = 2
) {
    @SuppressLint("MissingPermission")
    suspend fun waitForSpeechBurst(): Boolean = withContext(Dispatchers.IO) {
        val owner = Any()
        if (!VoiceMic.lease.acquire(owner)) return@withContext false
        try {
        val sampleRate = 16000
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return@withContext false
        val minBuf = minimum.coerceAtLeast(3200)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
        }.getOrNull() ?: return@withContext false

        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext false }
        val (aec, ns) = BluetoothAudioRouter.attachAudioEffects(rec.audioSessionId)

        val shorts = ShortArray(1600)
        var hits = 0
        var emptyReads = 0
        return@withContext try {
            rec.startRecording()
            while (isActive) {
                val read = rec.read(shorts, 0, shorts.size)
                if (read > 0 && read <= shorts.size) {
                    emptyReads = 0
                    var sum = 0.0
                    for (i in 0 until read) { val s = shorts[i].toDouble(); sum += s * s }
                    if (sqrt(sum / read) >= thresholdProvider().takeIf { it.isFinite() && it in 1.0..32768.0 }.let { it ?: 550.0 }) {
                        hits++
                        if (hits >= requiredFrames.coerceAtLeast(1)) return@withContext true
                    } else hits = 0
                } else if (read == 0) {
                    hits = 0
                    if (AudioCapturePolicy.emptyReadLimit(++emptyReads)) return@withContext false
                    delay(AudioCapturePolicy.EMPTY_READ_DELAY_MS)
                } else return@withContext false
            }
            false
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false } finally {
            runCatching { aec?.release() }; runCatching { ns?.release() }
            runCatching { rec.stop() }; runCatching { rec.release() }
        }
        } finally { VoiceMic.lease.release(owner) }
    }
}
