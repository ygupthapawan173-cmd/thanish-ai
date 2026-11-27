package com.example

import com.example.ai.DirectDeviceCommand
import com.example.ai.awaitParsed
import com.example.voice.BackgroundVoicePolicy
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InfinityBugfixTest {
    @Test fun explicitCommands() {
        assertEquals(DirectDeviceCommand.Action("toggle_flashlight", true), DirectDeviceCommand.parse("Hey JARVIS, flashlight ON చెయ్"))
        assertEquals(DirectDeviceCommand.Action("set_timer", 300), DirectDeviceCommand.parse("set a timer for 5 minutes"))
        assertEquals(DirectDeviceCommand.Action("open_app", "whatsapp"), DirectDeviceCommand.parse("WhatsApp open chey"))
        assertEquals(DirectDeviceCommand.Action("set_media_volume", 42), DirectDeviceCommand.parse("set volume to 42%"))
    }
    @Test fun rejectAmbiguousActions() {
        listOf("do not turn on flashlight", "how does flashlight work?", "flashlight on and open WhatsApp", "volume 101", "timer 0 seconds", "what happens if volume 100").forEach { assertNull(it, DirectDeviceCommand.parse(it)) }
    }
    @Test fun emergencyPhrases() {
        listOf("stop everything", "emergency stop", "cancel all", "అన్నీ ఆపు").forEach { assertEquals(BackgroundVoicePolicy.Action.STOP, BackgroundVoicePolicy.classify(it)) }
        assertEquals(BackgroundVoicePolicy.Action.QUESTION, BackgroundVoicePolicy.classify("how do I stop everything?"))
    }
    @Test fun cancellationReachesRealOkHttpCall() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Interceptor prevents external network access during this test.
        val client = OkHttpClient.Builder().addInterceptor {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            throw IOException("test response")
        }.build()
        val call = client.newCall(Request.Builder().url("https://example.invalid/").build())
        val job = launch(start=CoroutineStart.UNDISPATCHED) { call.awaitParsed { it.code } }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            withTimeout(5000) { job.cancelAndJoin() }
            assertTrue(call.isCanceled())
        } finally {
            release.countDown()
            job.cancel()
            client.dispatcher.executorService.shutdown()
        }
    }
}
