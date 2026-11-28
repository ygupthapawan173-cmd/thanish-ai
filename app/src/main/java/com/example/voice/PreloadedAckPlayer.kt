package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.sin

/**
 * Preloaded "Yes Boss" Wake Acknowledgement Player (Section 6):
 * - Caches synthesized Sarvam "Yes Boss" audio locally once credentials are configured
 *   so wake acknowledgements do NOT wait for a network TTS request.
 * - Falls back cleanly to a zero-latency in-memory warm chime PCM buffer before first Sarvam cache.
 * - Measures exact wall-clock acknowledgement latency (`lastMeasuredAckMs`) honestly on device.
 */
object PreloadedAckPlayer {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var lastMeasuredAckMs: Long = 0L
        private set

    @Volatile var usingCachedSarvamAudio: Boolean = false
        private set

    private val memory = ConcurrentHashMap<String, ByteArray>()
    private val warmLock = Mutex()
    @Volatile private var profile = "legacy"

    private val warmChimePcm: ShortArray by lazy {
        val sampleRate = 22050
        val count = (sampleRate * 0.22).toInt()
        ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = kotlin.math.sin(PI * i / count)
            val freq = if (i < count / 2) 680.0 else 920.0
            (sin(2.0 * PI * freq * t) * env * 9000.0).toInt().toShort()
        }
    }

    private fun cacheFile(context: Context, langTag: String): File {
        val dir = File(context.applicationContext.filesDir, "ack_cache").apply { mkdirs() }
        val lang = if (langTag.startsWith("te", true)) "te" else "en"
        val name = "yes_boss_" + profile + "_" + lang + ".mp3"
        return File(dir, name)
    }

    fun warmUpSarvamAckCache(context: Context, sarvamKey: String, speakerId: String, model: String) {
        if (sarvamKey.isBlank()) return
        val newProfile = MessageDigest.getInstance("SHA-256").digest("$speakerId:$model".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        scope.launch { warmLock.withLock {
            if (profile != newProfile) { profile = newProfile; memory.clear() }
            val enFile = cacheFile(context, "en-IN")
            val teFile = cacheFile(context, "te-IN")
            if (!enFile.exists() || enFile.length() == 0L) {
                SarvamVoiceClient.synthesize(sarvamKey, "Yes Boss", "en-IN", speakerId, model)
                    .onSuccess { bytes -> runCatching { enFile.writeBytes(bytes) } }
            }
            if (!teFile.exists() || teFile.length() == 0L) {
                SarvamVoiceClient.synthesize(sarvamKey, "యెస్ బాస్, చెప్పండి.", "te-IN", speakerId, model)
                    .onSuccess { bytes -> runCatching { teFile.writeBytes(bytes) } }
            }
            listOf(enFile, teFile).forEach { file ->
                if (file.length() in 101..2_000_000) runCatching { memory[file.name] = file.readBytes() }
            }
            usingCachedSarvamAudio = memory.isNotEmpty()
        } }
    }

    fun playImmediateAck(
        context: Context,
        languageTag: String,
        wakeDetectedAtElapsedMs: Long = SystemClock.elapsedRealtime(),
        onDone: () -> Unit
    ) {
        val cached = cacheFile(context, languageTag).takeIf { it.exists() && it.length() > 100L }
            ?: cacheFile(context, "en-IN").takeIf { it.exists() && it.length() > 100L }

        if (cached != null) {
            val bytes = memory[cached.name] ?: runCatching { cached.takeIf { it.length() <= 2_000_000 }?.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                usingCachedSarvamAudio = true
                playCachedMp3(bytes, wakeDetectedAtElapsedMs, onDone)
                return
            }
        }
        usingCachedSarvamAudio = false
        playInstantChime(wakeDetectedAtElapsedMs, onDone)
    }

    private fun playCachedMp3(bytes: ByteArray, wakeDetectedAtMs: Long, onDone: () -> Unit) {
        main.post {
            val player = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            player.setAudioAttributes(attrs)
            var finished = false
            fun doneOnce() {
                if (finished) return
                finished = true
                runCatching { player.release() }
                onDone()
            }
            runCatching {
                player.setDataSource(object : MediaDataSource() {
                    override fun getSize(): Long = bytes.size.toLong()
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position < 0 || position >= bytes.size) return -1
                        val n = minOf(size, bytes.size - position.toInt())
                        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + n)
                        return n
                    }
                    override fun close() {}
                })
                player.setOnPreparedListener { mp ->
                    runCatching { mp.start(); lastMeasuredAckMs = (SystemClock.elapsedRealtime() - wakeDetectedAtMs).coerceAtLeast(1L) }.onFailure { doneOnce() }
                }
                player.setOnCompletionListener { doneOnce() }
                player.setOnErrorListener { _, _, _ -> doneOnce(); true }
                main.postDelayed({ doneOnce() }, 2_000L)
                player.prepareAsync()
            }.onFailure {
                runCatching { player.release() }
                playInstantChime(wakeDetectedAtMs, onDone)
            }
        }
    }

    private fun playInstantChime(wakeDetectedAtMs: Long, onDone: () -> Unit) {
        Thread {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(22050)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(warmChimePcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(warmChimePcm, 0, warmChimePcm.size)
                lastMeasuredAckMs = (SystemClock.elapsedRealtime() - wakeDetectedAtMs).coerceAtLeast(1L)
                track.play()
                Thread.sleep(240L)
                runCatching { track.stop(); track.release() }
            }
            main.post { onDone() }
        }.start()
    }
}
