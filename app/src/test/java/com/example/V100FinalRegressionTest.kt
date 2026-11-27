package com.example

import com.example.autonomous.FinalIntegrationPolicy as P
import com.example.control.VisionEncodingPolicy as V
import com.example.ui.SettingsSearchPolicy as S
class V100FinalRegressionTest {
 @org.junit.Test fun policies() {

 var n=0
 fun t(ok:Boolean) { check(ok) { "Integration case ${n+1}" }; n++ }
 for (s in listOf("", " ", "x", "a".repeat(4000),"a".repeat(4001),"a\u0000b")) t(P.validRequest(s)==(s.isNotBlank()&&s.length<=4000&&'\u0000' !in s))
 for (s in listOf("", "a".repeat(39),"a".repeat(40),"a".repeat(41),"G".repeat(40),"0".repeat(40))) t(P.validSha(s)==(s.length==40&&s.all{it in 'a'..'f'||it in '0'..'9'}))
 for(s in listOf("main","omega-auto/upgrade-test","omega-auto/upgrade-../x","omega-auto/other","omega-auto/upgrade-foo.lock")) t(P.isolatedBranch(s)==(s=="omega-auto/upgrade-test"))
 for(s in listOf("failure","cancelled","success","timed_out","skipped","")) t(P.shouldRepair(s)==(s=="failure"))
 for(a in listOf(-1,0,50,100,101)) for(b in listOf(-1,0,25,80,101)) t(P.progress(a,b)==maxOf(a.coerceIn(0,100),b.coerceIn(0,100)))
 for(v in listOf(Int.MIN_VALUE,-1,0,1,99,100,101,Int.MAX_VALUE)) { t(P.battery(v)==if(v in 0..100)v else -1); t(P.percentLabel(v)==if(v in 0..100)"$v%" else "Unavailable") }
 for(v in listOf(null,Int.MIN_VALUE,-401,-400,0,300,1000,1001)) { val x=P.temperature(v); t(if(v!=null&&v in -400..1000)x==v/10f else x.isNaN()) }
 t(P.tempLabel(Float.NaN)=="Unavailable");t(P.tempLabel(31.5f)=="31.5°C");t(P.ram(100,25)==75);t(P.ram(0,0)==-1);t(P.ram(100,-1)==-1);t(P.ram(100,101)==-1);t(P.ram(Long.MAX_VALUE,0)==100)
 for((w,h) in listOf(1 to 1,100 to 200,4000 to 3000,1 to 10000,10000 to 1,Int.MAX_VALUE to Int.MAX_VALUE)) for(m in listOf(0,128,1024,2048,9999)) { val (x,y)=V.dimensions(w,h,m);t(x in 1..minOf(w,m.coerceIn(128,2048))&&y in 1..minOf(h,m.coerceIn(128,2048))) }
 t(V.dimensions(4000,2000,1024)==(1024 to 512));t(runCatching{V.dimensions(0,1,128)}.isFailure)
 for(s in listOf(-1,0,1,V.MAX_JPEG_BYTES,V.MAX_JPEG_BYTES+1))t(V.acceptsSize(s)==(s in 1..V.MAX_JPEG_BYTES))
 for(q in listOf(""," ","VOICE","voice  engine","speech\nengine","microphone","unknown"))t(S.matches(q,"Voice Engine","Speech","microphone")== (q!="unknown"))
 println("$n/$n final integration policy checks passed (not features)")
}
}
