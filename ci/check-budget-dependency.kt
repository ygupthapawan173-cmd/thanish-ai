import com.example.ai.ProviderBudgetPolicy as B
import com.example.ai.OfflineUnitConverter as U
import com.example.autonomous.SelectedDependencyAnalysis as D
fun main() {
    var n=0
    fun test(ok:Boolean) { check(ok) { "case ${n+1} failed" }; n++ }
    fun reject(cap:Double=5.0, limit:Double?=5.0, remaining:Double?=1.0, lifetime:Boolean=true, byok:Boolean=true, ordinary:Boolean=true) = B.rejection(cap,limit,remaining,lifetime,byok,ordinary)
    test(reject()==null); test(reject(cap=0.0,limit=null,remaining=null)==null)
    test(reject(cap=Double.NaN)!=null); test(reject(cap=-1.0)!=null); test(reject(limit=null)!=null); test(reject(limit=5.01)!=null)
    test(reject(limit=0.0)!=null); test(reject(remaining=null)!=null); test(reject(remaining=0.0)!=null); test(reject(remaining=6.0)!=null)
    test(reject(remaining=Double.POSITIVE_INFINITY)!=null); test(reject(lifetime=false)!=null); test(reject(byok=false)!=null); test(reject(ordinary=false)!=null)
    val a="package com.a\nimport com.b.B\nclass A"
    val b="package com.b\ndata class B(val value: Int)\nfun helper() = 1"
    val c="package com.c\nobject C"
    val desc=D.describe("a", mapOf("a" to a,"b" to b,"c" to c))
    test(D.analyze("b",b).symbols==setOf("B","helper")); test(desc.contains("b: package=com.b; possible dependency=true")); test(desc.contains("c: package=com.c; possible dependency=false")); test(D.describe("x",mapOf("a" to a))=="No selected dependency context")
    test(U.convert("2 km to m")=="2 km ≈ 2000 m"); test(U.convert("convert 1 in to cm")=="1 in ≈ 2.54 cm"); test(U.convert("1 kg లోకి g")=="1 kg ≈ 1000 g"); test(U.convert("1 kg to km")==null); test(U.convert("1 usd to inr")==null); test(U.convert("1 hr in min")=="1 hr ≈ 60 min")
    println("$n/$n budget, selected dependency and offline converter checks passed")
}
