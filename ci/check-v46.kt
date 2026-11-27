import com.example.voice.VoiceSessionPolicy as V
import com.example.autonomous.SelectedDependencyAnalysis as D
fun main() {
    var n=0
    fun t(ok:Boolean) { check(ok) { "Case ${n+1} failed" }; n++ }
    t(V.localOnly(false,true)); t(V.localOnly(true,false)); t(!V.localOnly(false,false)); t(V.localOnly(true,true))
    t(V.nextPack("auto","en-IN",emptySet(),true)=="model-en-us")
    t(V.nextPack("auto","en-IN",setOf("model-en-us"),true)=="model-te-in")
    t(V.nextPack("auto","te-IN",setOf("model-te-in"),true)=="model-en-us")
    t(V.nextPack("auto","te-IN",setOf("model-en-us","model-te-in"),true)==null)
    t(V.nextPack("te-IN","en-IN",emptySet(),true)=="model-te-in")
    t(V.nextPack("te-IN","en-IN",setOf("model-te-in"),true)==null)
    t(V.nextPack("en-IN","te-IN",setOf("model-en-us"),true)==null)
    t(V.nextPack("auto","en-IN",setOf("model-en-us"),false)==null)
    t(V.ttsTimeoutMs(0)==20000L); t(V.ttsTimeoutMs(-1)==20000L); t(V.ttsTimeoutMs(100)==55000L); t(V.ttsTimeoutMs(Int.MAX_VALUE)==600000L)
    val graph=mapOf("a" to "package a\nimport b.B\nclass A", "b" to "package b\nimport c.C\nclass B", "c" to "package c\nimport a.A\nclass C", "d" to "package d\nclass D")
    t(D.reachable("a",graph)==setOf("b","c")); t(D.reachable("d",graph).isEmpty()); t(D.reachable("missing",graph).isEmpty()); t(D.reachable("c",graph)==setOf("a","b")); t(D.describe("a",graph).contains("c: package=c; possible dependency=false; transitively reachable=true"))
    println("$n/$n V46 voice policy and selected dependency graph checks passed")
}
