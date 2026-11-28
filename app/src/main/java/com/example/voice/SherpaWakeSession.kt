package com.example.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig

class SherpaWakeSession(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onWake: () -> Unit,
    private val onFailure: () -> Unit
) {
    private val lock = Any()
    @Volatile private var running = false
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        synchronized(lock) {
            check(!running)
            running = true
        }
        Thread({
            var detector: KeywordSpotter? = null
            var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            try {
                val dir = "sherpa-kws"
                val kws = KeywordSpotter(context.assets, KeywordSpotterConfig(
                    featConfig = getFeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "$dir/encoder.int8.onnx",
                            decoder = "$dir/decoder.int8.onnx",
                            joiner = "$dir/joiner.int8.onnx"),
                        tokens = "$dir/tokens.txt", numThreads = 1, provider = "cpu", modelType = "zipformer2"),
                    keywordsFile = "$dir/keywords.txt", keywordsThreshold = 0.25f, numTrailingBlanks = 2
                ))
                detector = kws
                val input = kws.createStream()
                stream = input
                val rec = synchronized(lock) {
                    if (!running) return@Thread
                    val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(min > 0)
                    val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 3200))
                    record = r
                    check(r.state == AudioRecord.STATE_INITIALIZED)
                    r.startRecording()
                    check(r.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    r
                }
                onReady()
                val pcm = ShortArray(512)
                val samples = FloatArray(pcm.size)
                var empty = 0
                while (running) {
                    val n = rec.read(pcm, 0, pcm.size)
                    if (!running) break
                    check(n >= 0)
                    if (n == 0) {
                        check(++empty < 100)
                        Thread.sleep(10)
                        continue
                    }
                    empty = 0
                    for (i in 0 until n) samples[i] = pcm[i] / 32768f
                    input.acceptWaveform(if (n == samples.size) samples else samples.copyOf(n), 16000)
                    while (running && kws.isReady(input)) {
                        kws.decode(input)
                        val result = kws.getResult(input).keyword
                        if (result.isNotBlank()) {
                            kws.reset(input)
                            // Model contains only the configured Hey JARVIS phrase.
                            onWake()
                            return@Thread
                        }
                    }
                }
            } catch (_: Exception) {
                if (running) onFailure()
            } catch (_: LinkageError) {
                if (running) onFailure()
            } finally {
                synchronized(lock) {
                    running = false
                    runCatching { record?.stop() }
                    runCatching { record?.release() }
                    record = null
                }
                runCatching { stream?.release() }
                runCatching { detector?.release() }
            }
        }, "jarvis-sherpa-kws").start()
    }

    fun stop() {
        synchronized(lock) {
            running = false
            runCatching { record?.stop() }
            runCatching { record?.release() }
            record = null
        }
    }
}
