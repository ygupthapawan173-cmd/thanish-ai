import com.example.data.TtsEmotionStyle
import com.example.voice.LiveVoiceChoices
fun main() {
 var n=0
 fun t(ok:Boolean){check(ok){"check ${n+1}"};n++}
 t(TtsEmotionStyle.entries.size==11)
 t(TtsEmotionStyle.entries.map { it.name }.distinct().size==11)
 for (s in TtsEmotionStyle.entries) {
  t(TtsEmotionStyle.valueOf(s.name)==s)
  for (v in listOf(-1f,0f,.7f,1f,1.5f,100f,Float.NaN,Float.POSITIVE_INFINITY)) {
   t(s.speed(v) in .5f..1.8f);t(s.pitch(v) in .5f..1.6f)
  }
 }
 t(TtsEmotionStyle.DEEP.pitch(1f)<1f);t(TtsEmotionStyle.QUICK.speed(1f)>1f)
 for (v in LiveVoiceChoices.names) { t(LiveVoiceChoices.normalize(v)==v);t(LiveVoiceChoices.normalize(" ${v.lowercase()} ")==v) }
 t(LiveVoiceChoices.normalize(null)=="");t(LiveVoiceChoices.normalize("invalid") == "")
 println("$n/$n voice preset and Live voice selection JVM checks passed")
}
