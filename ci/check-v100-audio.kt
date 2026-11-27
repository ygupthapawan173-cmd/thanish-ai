import com.example.voice.AudioCapturePolicy as P
fun main() {
 var n=0
 fun t(b:Boolean) { check(b){"Audio case ${n+1}"};n++ }
 for (read in -5..12) t(P.validPcmRead(read,8)==(read in 2..8 && read%2==0))
 for (count in listOf(-1,0,1,98,99,100,101,Int.MAX_VALUE)) t(P.emptyReadLimit(count)==(count>=100))
 for (s in listOf(Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY,-1f,0f,.2f,.5f,1f,2f)) t(P.wakeThreshold(s) in .5..0.8)
 t(P.wakeThreshold(.2f)>P.wakeThreshold(1f));t(P.wakeThreshold(Float.NaN)==P.wakeThreshold(.5f))
 for (c in listOf(Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,-.1,0.0,.49,.5,.75,1.0,1.1)) t(P.confidenceAccepted(c,.5)==(c in .5..1.0))
 for (th in listOf(Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,-.1,1.1)) t(!P.confidenceAccepted(.8,th))
 for (enabled in listOf(false,true)) for (verified in listOf(false,true)) t(P.ownerWakeAllowed(enabled,verified)==(!enabled||verified))
 println("$n/$n audio policy checks passed (not feature count)")
}
