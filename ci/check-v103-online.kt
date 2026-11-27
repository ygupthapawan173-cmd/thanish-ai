import com.example.voice.OnlineSpeechPolicy
fun main() {
 var n=0
 for (privacy in listOf(false,true)) for(system in listOf(false,true)) for(local in listOf(false,true)) for(attempt in 0..6) {
  check(OnlineSpeechPolicy.useLocal(privacy,system,local,attempt,onlineOnly=true)==if(!privacy && system) false else null)
  n++
 }
 println("$n/$n online-only speech routing checks passed")
}
