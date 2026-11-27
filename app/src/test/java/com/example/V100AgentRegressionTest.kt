package com.example

import com.example.ai.AgentExecutionPolicy as P
import com.example.omega.TriggerDebouncePolicy
import com.example.data.SemanticMemoryEngine as M
import com.example.data.MemoryFact
class V100AgentRegressionTest {
 @org.junit.Test fun policies() {
 var n=0
 fun t(b:Boolean) { check(b){"Agent/memory case ${n+1}"}; n++ }
 for (used in listOf(-1,0,7,16,17,23,24,25,Int.MAX_VALUE)) for (incoming in listOf(-1,0,1,7,8,9,Int.MAX_VALUE))
   t(P.batchAllowed(used,incoming)==(used in 0..24 && incoming in 1..8 && used.toLong()+incoming<=24))
 for (count in listOf(-1,0,1,8,23,24,25,Int.MAX_VALUE)) t(P.routineAllowed(count)==(count in 1..24))
 for (name in listOf("", " ", "run_routine", "set_media_volume", "open_app")) t(P.validRoutineTool(name)==(name.isNotBlank()&&name!="run_routine"))
 t(P.boundedContext("abc",0)=="");t(P.boundedContext("abc",2)=="ab");t(P.boundedContext("abc",3)=="abc");t(P.boundedContext("a😀b",2)=="a");t(P.boundedContext("a😀b",3)=="a😀")
 val d=TriggerDebouncePolicy()
 t(!d.claim("",0));t(!d.claim("A",-1));t(d.claim("A",0));t(!d.claim("A",0));t(d.claim("B",0));t(!d.claim("A",2499));t(d.claim("A",2500));t(!d.claim("A",4999));t(d.claim("A",5000));t(d.claim("A",1));t(!d.claim("A",2))
 t(M.tokenize("dog ".repeat(5000)).size==1024)
 t(M.tokenize("DOG తెలుగు")==listOf("dog","తెలుగు"))
 t(M.tokenize(" ".repeat(16384)+"dog").isEmpty())
 val facts=(1L..60L).map { MemoryFact(it,"dog","pet",it) }
 t(M.search("dog",facts,Int.MAX_VALUE).size==50); t(M.search("dog",facts,1).single().id==60L)
 t(M.search("dog",facts,-1).isEmpty());t(M.search("unrelated",facts).isEmpty())
 println("$n/$n agent, memory and trigger policy checks passed (not features)")
}
}
