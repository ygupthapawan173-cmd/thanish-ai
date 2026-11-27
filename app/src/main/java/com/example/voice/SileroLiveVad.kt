package com.example.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/** Optional local neural VAD. Does not acquire the microphone or filter server audio. */
internal class SileroLiveVad private constructor(private val env: OrtEnvironment, private val session: OrtSession) : AutoCloseable {
    private val windows = PcmVadWindow()
    private var state = FloatArray(256)
    private var hangover = 0
    private var consecutive = 0
    private fun infer(input: FloatArray): Float {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1,576)).use { x ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(state), longArrayOf(2,1,128)).use { s ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(16000)), longArrayOf()).use { rate ->
                    session.run(mapOf("input" to x, "state" to s, "sr" to rate)).use { result ->
                        val output = result[0] as OnnxTensor
                        val next = result[1] as OnnxTensor
                        val probability = output.floatBuffer.get()
                        val buffer = next.floatBuffer
                        check(buffer.remaining() == 256 && probability.isFinite() && probability in 0f..1f)
                        state = FloatArray(256).also { buffer.get(it) }
                        return probability
                    }
                }
            }
        }
    }
    fun update(pcm: ByteArray, size: Int): Boolean {
        var speech = hangover > 0
        windows.feed(pcm, size) { input ->
            if (infer(input) >= 0.5f) {
                consecutive = (consecutive + 1).coerceAtMost(2)
                if (consecutive >= 2) hangover = 10
            } else {
                consecutive = 0
                if (hangover > 0) hangover--
            }
            speech = speech || hangover > 0
        }
        return speech
    }
    override fun close() { session.close() }
    companion object {
        fun open(context: Context): SileroLiveVad? = try {
            val model = context.assets.open("neural-vad/silero_vad.onnx").use { it.readBytes() }
            require(model.size in 100_000..8_000_000)
            val expected = context.assets.open("neural-vad/model.sha256").bufferedReader().use { it.readText().trim() }
            val digest = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it) }
            require(expected == digest) { "VAD model integrity failure" }
            val env = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                options.setInterOpNumThreads(1)
                env.createSession(model, options)
            }
            try {
                require(session.inputNames == setOf("input", "state", "sr"))
                val vad = SileroLiveVad(env, session)
                vad.infer(FloatArray(576)) // genuine inference smoke test
                vad.state = FloatArray(256)
                Log.i("InfinityVAD", "Silero neural VAD initialized")
                vad
            } catch (e: Exception) { session.close(); throw e }
        } catch (e: Exception) { Log.w("InfinityVAD", "Adaptive RMS fallback: ${e.javaClass.simpleName}"); null }
        catch (e: LinkageError) { Log.w("InfinityVAD", "ONNX runtime unavailable; adaptive RMS fallback"); null }
    }
}
