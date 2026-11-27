package com.example

import com.example.ai.SmartModelPolicy
import com.example.autonomous.EngineeringPolicy
import com.example.autonomous.ReleaseVersionPolicy
import com.example.autonomous.UpdateTransferPolicy
import com.example.data.*
import com.example.security.DiagnosticRedaction
import com.example.voice.*
class V100PolicyRegressionTest {
 @org.junit.Test fun policies() {
 var n=0
 fun t(ok:Boolean) { check(ok) { "V100 check ${n+1} failed" }; n++ }
 val task=SmartModelPolicy.Task.CHAT
 for(id in listOf(""," ","bad id","bad\nmodel","a".repeat(513))) t(!SmartModelPolicy.eligible(SmartModelPolicy.Model(id),task))
 t(SmartModelPolicy.eligible(SmartModelPolicy.Model("provider/model-1"),task))
 for(a in -3..5) for(code in listOf(1,2,3,5,8,11,12,13)) t(VoiceRecoveryPolicy.retryable(code,true,a)==(a in 0..1))
 for(x in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-2f,0f)) { val s=LiveSilencePolicy(); repeat(12){t(!s.update(x))}; repeat(2){t(!s.update(.9f))};t(s.update(.9f)) }
 for(scale in listOf(1e-300,1e-100,1.0,1e100,1e300,Double.MAX_VALUE)) { val a=doubleArrayOf(scale,scale);t(SpeakerProfilePolicy.similarity(a,a)>.999);t(SpeakerProfilePolicy.similarity(a,doubleArrayOf(-scale,-scale))<-.999) }
 t(SpeakerProfilePolicy.similarity(doubleArrayOf(0.0),doubleArrayOf(1.0))==-1.0)
 t(SpeakerProfilePolicy.similarity(doubleArrayOf(Double.NaN),doubleArrayOf(1.0))==-1.0)
 t(!VoiceHealthPolicy.stalled(-1,0,0));t(!VoiceHealthPolicy.stalled(100,200,200));t(VoiceHealthPolicy.stalled(9000,0,9000))
 for(q in RenderQuality.entries) for(power in listOf(false,true)) for(low in listOf(false,true)) for(thermal in listOf(false,true)) t(q.reduced(power,low,thermal)==(q==RenderQuality.SAVER || power || thermal || q==RenderQuality.ADAPTIVE && low))
 val m=listOf(MemoryFact(1,"dog","Jannu",10),MemoryFact(2,"dog","Rocky",20),MemoryFact(3,"cats","Luna",30),MemoryFact(4,"carpet","blue",40))
 t(SemanticMemoryEngine.search("dog",m).map{it.id}==listOf(2L,1L));t(SemanticMemoryEngine.search("carpenter",m).isEmpty());t(SemanticMemoryEngine.search("",m).isEmpty());t(SemanticMemoryEngine.search("dog",m,0).isEmpty());t(SemanticMemoryEngine.search("DOG",m,1).single().id==2L)
 for(s in listOf("v99","100.0","v100.0.0","latest","","v100.0-beta","v9999999999")) t(!ReleaseVersionPolicy.newer(s,"100.0-hyper-integration-candidate"))
 for(s in listOf("v101","v100.1","100.0.1","V200.0.0")) t(ReleaseVersionPolicy.newer(s,"100.0-hyper-integration-candidate"))
 val base="https://github.com/owner/repo/releases/download/v101/app.apk"
 t(UpdateTransferPolicy.allowedAsset(base));for(s in listOf("?token=a","#section"))t(!UpdateTransferPolicy.allowedAsset(base+s))
 t(!UpdateTransferPolicy.allowedAsset("https://github.com/owner/repo/releases/download/v101/../app.apk"))
 for(secret in listOf("Bearer test-token","ghp_"+"a".repeat(30),"sk-proj-"+"b".repeat(30),"AIza"+"c".repeat(30))) t(DiagnosticRedaction.clean("test $secret")=="test [REDACTED]")
 t(DiagnosticRedaction.clean("ordinary error")=="ordinary error")
 t(EngineeringPolicy.completeSource("package example"));t(!EngineeringPolicy.completeSource("package example\u0000"))
 println("$n/$n V100 pure JVM checks passed (policy cases, not feature count)")
}

}
