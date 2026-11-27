import com.example.omega.DailySchedulePolicy as S
import com.example.autonomous.EngineeringPolicy as E
import java.time.*
fun main() {
    var n=0
    fun test(label:String,ok:Boolean) { check(ok){label}; n++; println("PASS: $label") }
    val zone=ZoneId.of("Asia/Kolkata")
    fun ms(t:String,z:ZoneId=zone)=LocalDateTime.parse(t).atZone(z).toInstant().toEpochMilli()
    test("same day schedule",S.nextMillis(ms("2026-11-27T07:00"),7,30,zone)==ms("2026-11-27T07:30"))
    test("past schedule next day",S.nextMillis(ms("2026-11-27T08:00"),7,30,zone)==ms("2026-11-28T07:30"))
    test("equal time next day",S.nextMillis(ms("2026-11-27T07:30"),7,30,zone)==ms("2026-11-28T07:30"))
    test("invalid time rejected",runCatching{S.nextMillis(0,24,0,zone)}.isFailure)
    val ny=ZoneId.of("America/New_York")
    test("DST uses local wall time",S.nextMillis(ms("2026-03-07T08:00",ny),7,30,ny)==ms("2026-03-08T07:30",ny))
    test("step fingerprint stable", S.fingerprint("abc")=="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    test("modified steps differ",S.fingerprint("a")!=S.fingerprint("b"))
    test("torch boolean",S.eligible("toggle_flashlight",true)&&!S.eligible("toggle_flashlight","true"))
    test("volume boundaries",S.eligible("set_media_volume",0)&&S.eligible("set_media_volume",100)&&!S.eligible("set_media_volume",101))
    test("sensitive actions blocked",!S.eligible("open_app","app")&&!S.eligible("send_sms",true))
    val a="app/src/main/java/com/example/A.kt"; val b="app/src/test/java/com/example/B.kt"
    test("two explicit paths",E.paths("$a, $b")==listOf(a,b))
    test("deduplicated paths",E.paths("$a,$a")==listOf(a))
    test("workflow denied",runCatching{E.paths(".github/workflows/build.yml")}.isFailure)
    test("path traversal denied",runCatching{E.paths("app/src/main/java/../A.kt")}.isFailure)
    test("bounded file count",runCatching{E.paths((1..5).joinToString(","){"app/src/main/java/A$it.kt"})}.isFailure)
    test("repo path validated",E.validRepo("owner/repo")&&!E.validRepo("../repo")&&!E.validRepo("owner/repo?x=1"))
    test("branch validated",E.validBase("main")&&E.validBase("dev/test")&&!E.validBase("main?x=y")&&!E.validBase("a..b"))
    test("exact CI commit match",E.matchingCommit("abc","abc")&&!E.matchingCommit("abc","def")&&!E.matchingCommit("",""))
    test("oversize file blocked",E.completeSource("abc")&&!E.completeSource("x".repeat(40001))&&!E.completeSource(" "))
    println("$n/19 release policy checks passed")
}
