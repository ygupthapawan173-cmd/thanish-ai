package com.example.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.security.MessageDigest

/** Shares Sherpa's native runtime with KWS, avoiding conflicting ONNX JNI libraries. */
internal class SileroLiveVad private constructor(private val vad: Vad) : AutoCloseable {
    private val window = FloatArray(512)
    private var used = 0
    @Synchronized
    fun update(pcm: ByteArray, size: Int): Boolean {
        var speech = vad.isSpeechDetected()
        var i = 0
        val limit = minOf(size, pcm.size)
        while (i + 1 < limit) {
            window[used++] = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 255)).toShort() / 32768f
            i += 2
            if (used == window.size) {
                vad.acceptWaveform(window)
                speech = speech || vad.isSpeechDetected()
                while (!vad.empty()) vad.pop()
                used = 0
            }
        }
        return speech
    }
    @Synchronized
    override fun close() { vad.release() }

    companion object {
        fun open(context: Context): SileroLiveVad? = try {
            val path = "neural-vad/silero_vad.onnx"
            val model = context.assets.open(path).use { it.readBytes() }
            require(model.size in 100_000..8_000_000)
            val expected = context.assets.open("neural-vad/model.sha256").bufferedReader().use { it.readText().trim() }
            val digest = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it) }
            require(expected == digest) { "VAD model integrity failure" }
            val vad = Vad(context.assets, VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(model = path, threshold = 0.5f,
                    minSilenceDuration = 0.25f, minSpeechDuration = 0.064f, windowSize = 512),
                sampleRate = 16000, numThreads = 1, provider = "cpu"))
            try {
                vad.acceptWaveform(FloatArray(1024))
                vad.reset()
                Log.i("InfinityVAD", "Silero initialized using Sherpa native runtime")
                SileroLiveVad(vad)
            } catch (e: Exception) { vad.release(); throw e }
        } catch (e: Exception) {
            Log.w("InfinityVAD", "RMS fallback: ${e.javaClass.simpleName}")
            null
        } catch (e: LinkageError) {
            Log.w("InfinityVAD", "Native VAD unavailable; RMS fallback")
            null
        }
    }
}
