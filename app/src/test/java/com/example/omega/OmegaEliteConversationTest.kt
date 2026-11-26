package com.example.omega
import org.junit.Assert.*
import org.junit.Test
class OmegaEliteConversationTest {
 @Test fun confidenceRejectsMissingAndWeakWords(){
  assertTrue(OmegaEliteConversation.confidenceAccepted(listOf(.8,.9),2))
  assertFalse(OmegaEliteConversation.confidenceAccepted(listOf(.9,.4),2))
  assertFalse(OmegaEliteConversation.confidenceAccepted(emptyList(),2))
  assertFalse(OmegaEliteConversation.confidenceAccepted(listOf(.9,Double.NaN),2))
 }
 @Test fun followupIsBoundedAndPendingPreserved(){
  assertTrue(OmegaEliteConversation.canFollowup(true,2,""))
  assertFalse(OmegaEliteConversation.canFollowup(true,3,""))
  assertFalse(OmegaEliteConversation.canFollowup(true,1,"pending"))
  assertFalse(OmegaEliteConversation.canFollowup(false,0,""))
 }
 @Test fun retryIsBoundedAndNotForAuthErrors(){
  assertTrue(OmegaEliteConversation.retryLive("Live connection failed",0))
  assertFalse(OmegaEliteConversation.retryLive("Live connection failed (HTTP 401)",0))
  assertFalse(OmegaEliteConversation.retryLive("Playback failed",1))
  assertFalse(OmegaEliteConversation.retryLive("Conversation idle",0))
 }
 @Test fun reviewQueuePreservesAndBoundsCommands(){
  val q=OmegaEliteConversation.appendPending(listOf("first","second"),"third")!!
  assertEquals(listOf("first","second","third"),q)
  assertNull(OmegaEliteConversation.appendPending(q,"fourth"))
  assertEquals(q,OmegaEliteConversation.removePending(q,"stale"))
  assertEquals(listOf("second","third"),OmegaEliteConversation.removePending(q,"first"))
 }
 @Test fun recoveryBackoffAndCalibration(){
  assertEquals(350L,OmegaEliteConversation.recoveryDelay(1))
  assertEquals(1500L,OmegaEliteConversation.recoveryDelay(2))
  assertNull(OmegaEliteConversation.recoveryDelay(3))
  assertFalse(OmegaEliteConversation.resetRecoveryBudget(59000L))
  assertTrue(OmegaEliteConversation.resetRecoveryBudget(60000L))
  assertTrue(OmegaEliteConversation.confidenceAccepted(listOf(.6,.7),2,OmegaWakeConfidence.FAST.minimum))
  assertFalse(OmegaEliteConversation.confidenceAccepted(listOf(.6,.7),2,OmegaWakeConfidence.HIGH.minimum))
  assertTrue(OmegaEliteConversation.retryLive("Audio pipeline stalled",0))
 }
 @Test fun stopWordsAreExact(){
  assertTrue(OmegaEliteConversation.isEndPhrase("Stop listening"))
  assertFalse(OmegaEliteConversation.isEndPhrase("please explain stop listening"))
 }
}
