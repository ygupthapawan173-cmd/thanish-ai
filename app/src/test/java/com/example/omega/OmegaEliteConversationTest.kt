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
 @Test fun stopWordsAreExact(){
  assertTrue(OmegaEliteConversation.isEndPhrase("Stop listening"))
  assertFalse(OmegaEliteConversation.isEndPhrase("please explain stop listening"))
 }
}
