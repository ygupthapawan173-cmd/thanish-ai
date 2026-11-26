package com.example.omega
import org.junit.Assert.*
import org.junit.Test
class OmegaSpeakerPolicyTest {
 private fun axis(i:Int)=List(128){if(it==i)1.0 else 0.0}
 @Test fun vectorMatchAndReject(){
  assertTrue(OmegaSpeakerPolicy.match(axis(0),axis(0)))
  assertFalse(OmegaSpeakerPolicy.match(axis(0),axis(1)))
  assertNull(OmegaSpeakerPolicy.normalize(listOf(1.0)))
  assertNull(OmegaSpeakerPolicy.normalize(List(128){0.0}))
  assertNull(OmegaSpeakerPolicy.normalize(List(128){Double.NaN}))
 }
 @Test fun centroidRequiresThreeSamples(){
  assertNull(OmegaSpeakerPolicy.centroid(listOf(axis(0))))
  assertEquals(axis(0),OmegaSpeakerPolicy.centroid(List(3){axis(0)}))
 }
}
