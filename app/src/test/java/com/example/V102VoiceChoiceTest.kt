package com.example
import com.example.data.TtsEmotionStyle
import com.example.voice.LiveVoiceChoices
import org.junit.Test
import org.junit.Assert.*
class V102VoiceChoiceTest {
 @Test fun stylesStayBounded() {
  assertEquals(11,TtsEmotionStyle.entries.size)
  for(s in TtsEmotionStyle.entries) for(v in listOf(-1f,0f,.7f,1f,1.5f,100f,Float.NaN,Float.POSITIVE_INFINITY)) {
   assertTrue(s.speed(v) in .5f..1.8f);assertTrue(s.pitch(v) in .5f..1.6f)
  }
 }
 @Test fun liveVoiceSelectionUsesAllowList() {
  for(v in LiveVoiceChoices.names) assertEquals(v,LiveVoiceChoices.normalize(" ${v.lowercase()} "))
  assertEquals("",LiveVoiceChoices.normalize("unknown"));assertEquals("",LiveVoiceChoices.normalize(null))
 }
}
