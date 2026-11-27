import com.example.voice.VoiceRecoveryPolicy
import com.example.omega.WakeRetryPolicy
import com.example.ai.LiveRetryPolicy
import com.example.ai.GatewayRetryPolicy
import com.example.ai.SmartModelPolicy
fun main() {
 var n=0
 fun verify(b:Boolean) { check(b) { "failed check ${n+1}" }; n++ }
 verify(VoiceRecoveryPolicy.localeFromText("నమస్తే", "en-IN")=="te-IN")
 verify(VoiceRecoveryPolicy.localeFromText("hello", "te-IN")=="en-IN")
 verify(VoiceRecoveryPolicy.localeFromText("123", "te-IN")=="te-IN")
 verify(WakeRetryPolicy.delayMs(1)==1000L)
 verify(WakeRetryPolicy.delayMs(2)==2000L)
 verify(WakeRetryPolicy.delayMs(100)==32000L)
 verify(LiveRetryPolicy.mayRetry("HTTP 400",0,"a","b"))
 verify(!LiveRetryPolicy.mayRetry("HTTP 400",0,"models/b","b"))
 verify(!LiveRetryPolicy.mayRetry("HTTP 404",1,"a","b"))
 verify(!LiveRetryPolicy.mayRetry("HTTP 401",0,"a","b"))
 verify(LiveRetryPolicy.delayMs("HTTP 429",2)==30000L)
 verify(GatewayRetryPolicy.modelSpecific400("model_not_found"))
 verify(!GatewayRetryPolicy.modelSpecific400("invalid_request_error"))
 verify(GatewayRetryPolicy.cooldownMs(429,"40")==40000L)
 verify(GatewayRetryPolicy.cooldownMs(429,"99999")==300000L)
 verify(GatewayRetryPolicy.cooldownMs(404,null)==300000L)
 verify(GatewayRetryPolicy.cooldownMs(500,null)==0L)
 verify(SmartModelPolicy.task("polchu", false)==SmartModelPolicy.Task.REASONING)
 verify(SmartModelPolicy.task("ప్రోగ్రామ్", false)==SmartModelPolicy.Task.CODE)
 println("$n/$n V48 JVM policy checks passed")
}
