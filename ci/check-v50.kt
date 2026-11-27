import com.example.voice.OnlineSpeechPolicy
import com.example.voice.VoiceRecoveryPolicy
import com.example.ai.SmartModelPolicy
import com.example.ai.GatewayRetryPolicy
import com.example.autonomous.UpdateTransferPolicy
fun main() {
 var n=0
 fun t(x:Boolean) { check(x) { "V50 check ${n+1} failed" }; n++ }
 for (system in listOf(false,true)) for(local in listOf(false,true)) for(attempt in 0..2) {
  t(OnlineSpeechPolicy.useLocal(true,system,local,attempt)==if(local) true else null)
 }
 t(OnlineSpeechPolicy.useLocal(false,true,true,0)==false)
 t(OnlineSpeechPolicy.useLocal(false,true,true,1)==true)
 t(OnlineSpeechPolicy.useLocal(false,true,true,2)==false)
 t(OnlineSpeechPolicy.useLocal(false,true,false,1)==false)
 t(OnlineSpeechPolicy.useLocal(false,false,true,0)==true)
 t(OnlineSpeechPolicy.useLocal(false,false,false,0)==null)
 t(VoiceRecoveryPolicy.nextLocale("en-US")=="te-IN")
 t(VoiceRecoveryPolicy.nextLocale("te-IN")=="en-IN")
 t(GatewayRetryPolicy.cooldownMs(429," 45 ")==45000L)
 t(GatewayRetryPolicy.cooldownMs(429,"-5")==30000L)
 t(GatewayRetryPolicy.cooldownMs(429,"900")==300000L)
 val valid=SmartModelPolicy.Model("valid",promptPrice=1.0,completionPrice=1.0)
 val bad=SmartModelPolicy.Model("bad",promptPrice=-1.0,completionPrice=0.0)
 val nan=SmartModelPolicy.Model("nan",promptPrice=Double.NaN,completionPrice=0.0)
 t(SmartModelPolicy.rank(listOf(bad,nan,valid),SmartModelPolicy.Task.CHAT,SmartModelPolicy.Mode.ECONOMY,"").first()==valid)
 t(UpdateTransferPolicy.allowedAsset("https://github.com/owner/repo/releases/download/v50/app.apk"))
 for (url in listOf("http://github.com/owner/repo/releases/download/v50/app.apk","https://github.com.evil.test/owner/repo/releases/download/v50/app.apk","https://evil.test/a.apk","https://name@github.com/owner/repo/releases/download/v50/app.apk","https://github.com:8080/owner/repo/releases/download/v50/app.apk","https://github.com/owner/repo/blob/main/app.apk")) t(!UpdateTransferPolicy.allowedAsset(url))
 t(UpdateTransferPolicy.validHash("a".repeat(64)))
 t(!UpdateTransferPolicy.validHash(null))
 t(!UpdateTransferPolicy.validHash("x".repeat(64)))
 println("$n/$n V50 pure JVM policy checks passed")
}
