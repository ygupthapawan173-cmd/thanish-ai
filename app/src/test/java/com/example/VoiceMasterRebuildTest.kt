package com.example

import com.example.voice.MicLease
import com.example.voice.BackgroundVoicePolicy
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class VoiceMasterRebuildTest {
    @Test fun leaseRejectsOtherOwnerAndStaleRelease() {
        val lease = MicLease(); val a = Any(); val b = Any()
        assertTrue(lease.acquire(a)); assertFalse(lease.acquire(b))
        lease.release(b); assertTrue(lease.owns(a)); assertFalse(lease.acquire(b))
        lease.release(a); assertTrue(lease.acquire(b)); assertFalse(lease.owns(a))
        lease.release(a); assertTrue(lease.owns(b)); lease.release(b)
    }
    @Test fun concurrentRequestsHaveOneWinner() {
        val lease=MicLease(); val start=CountDownLatch(1); val won=AtomicInteger(0)
        val threads=(0 until 12).map { Thread { start.await(); if(lease.acquire(Any())) won.incrementAndGet() }.apply { start() } }
        start.countDown(); threads.forEach { it.join() }; assertEquals(1,won.get())
    }
    @Test fun exactWakePrefixAndLocalInlineOnly() {
        val phrases=listOf("hey jarvis","hey jar vis")
        assertTrue("hey jarvis battery" in BackgroundVoicePolicy.wakeGrammar(phrases))
        assertEquals("hey jarvis", BackgroundVoicePolicy.matchWake("hey jarvis battery",phrases))
        assertNull(BackgroundVoicePolicy.matchWake("not hey jarvis",phrases))
        assertNull(BackgroundVoicePolicy.matchWake("hey jarvison",phrases))
        assertFalse("send message" in BackgroundVoicePolicy.inlineCommands)
    }
    @Test fun lockedOrLocalOnlyRejectCloud() {
        assertFalse(BackgroundVoicePolicy.canSendCloud(true,false)); assertFalse(BackgroundVoicePolicy.canSendCloud(false,true))
        assertTrue(BackgroundVoicePolicy.canSendCloud(false,false))
    }
}
