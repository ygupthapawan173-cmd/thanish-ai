package com.example.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Optional openWakeWord inference. Runs on its own worker and owns a distinct microphone lease. */
internal class NeuralWakeCapture(private val context: Context, private val threshold: Float,
    private val onReady: () -> Unit, private val onFinished: (Boolean, Boolean) -> Unit) {
    private val stopped = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var record: AudioRecord? = null
    private val lease = Any()
    fun start(): Boolean {
        if (stopped.get() || !started.compareAndSet(false, true)) return false
        if (!VoiceMic.lease.acquire(lease)) { started.set(false); return false }
        try { Thread(::run, "Infinity-neural-wake").start() }
        catch (e: Exception) { VoiceMic.lease.release(lease); started.set(false); throw e }
        return true
    }
    fun stop() { stopped.set(true); runCatching { record?.stop() } }
    private fun load(env: OrtEnvironment, name: String): OrtSession {
        val path = "neural-wake/$name"
        val bytes = context.assets.open(path).use { it.readBytes() }
        require(bytes.size in 1000..30_000_000)
        val expected = context.assets.open("$path.sha256").bufferedReader().use { it.readText().trim() }
        require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == expected)
        return OrtSession.SessionOptions().use { opt ->
            opt.setIntraOpNumThreads(1); opt.setInterOpNumThreads(1)
            env.createSession(bytes, opt)
        }
    }
    private fun predict(env: OrtEnvironment, s: OrtSession, data: FloatArray, shape: LongArray): FloatArray {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { t ->
            s.run(mapOf(s.inputNames.single() to t)).use { r ->
                val b = (r[0] as OnnxTensor).floatBuffer
                return FloatArray(b.remaining()).also { b.get(it); require(it.all(Float::isFinite)) }
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun run() {
        var detected = false; var failed = false
        try {
            val env = OrtEnvironment.getEnvironment()
            load(env, "melspectrogram.onnx").use { mel ->
                load(env, "embedding_model.onnx").use { embed ->
                    load(env, "hey_jarvis_v0.1.onnx").use { head ->
                        if (stopped.get()) return
                        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        require(min > 0)
                        val mic = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 5120))
                        record = mic; check(mic.state == AudioRecord.STATE_INITIALIZED)
                        if (stopped.get()) return
                        mic.startRecording(); check(mic.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                        main.post { if (!stopped.get()) onReady() }
                        val previous = ShortArray(480); val chunk = ShortArray(1280)
                        val melHistory = ArrayDeque<FloatArray>(); val embHistory = ArrayDeque<FloatArray>()
                        var consecutive = 0
                        while (!stopped.get()) {
                            var n = 0
                            while (n < chunk.size && !stopped.get()) {
                                val read = mic.read(chunk, n, chunk.size - n)
                                check(read > 0); n += read
                            }
                            if (stopped.get()) break
                            val input = FloatArray(1760) { i -> if (i < 480) previous[i].toFloat() else chunk[i - 480].toFloat() }
                            chunk.copyInto(previous, 0, 800, 1280)
                            val m = predict(env, mel, input, longArrayOf(1,1760))
                            require(m.size >= 256 && m.size % 32 == 0)
                            for (i in m.size - 256 until m.size step 32) {
                                melHistory.addLast(FloatArray(32) { j -> m[i+j] / 10f + 2f })
                                if (melHistory.size > 76) melHistory.removeFirst()
                            }
                            if (melHistory.size != 76) continue
                            val flat = melHistory.toList()
                            val embedding = predict(env, embed, FloatArray(76*32) { flat[it / 32][it % 32] }, longArrayOf(1,76,32,1))
                            require(embedding.size == 96)
                            embHistory.addLast(embedding); if (embHistory.size > 16) embHistory.removeFirst()
                            if (embHistory.size != 16) continue
                            val embeddings = embHistory.toList()
                            val score = predict(env, head, FloatArray(16*96) { embeddings[it / 96][it % 96] }, longArrayOf(1,16,96)).single()
                            require(score in 0f..1f)
                            consecutive = if (score >= threshold) consecutive+1 else 0
                            if (consecutive >= 2) { detected = true; break }
                        }
                    }
                }
            }
        } catch (e: Exception) { failed = !stopped.get(); android.util.Log.w("InfinityWake", "Neural wake failed: ${e.javaClass.simpleName}") }
        catch (e: LinkageError) { failed = !stopped.get() }
        finally {
            record?.let { runCatching { it.stop() }; runCatching { it.release() } }; record = null
            VoiceMic.lease.release(lease)
            main.post { onFinished(detected && !stopped.get(), failed) }
        }
    }
    companion object {
        fun available(c: Context) = runCatching {
            val names = c.assets.list("neural-wake").orEmpty().toSet()
            setOf("melspectrogram.onnx", "embedding_model.onnx", "hey_jarvis_v0.1.onnx").all { it in names && "$it.sha256" in names }
        }.getOrDefault(false)
    }
}
