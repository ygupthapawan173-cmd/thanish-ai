import com.example.omega.WakeRearmPolicy
import com.example.omega.WakeRetryPolicy
import com.example.voice.MicLease
fun main() {
    var count=0
    fun test(ok:Boolean){check(ok){"check ${count+1}"};count++}
    // Every privacy, lifecycle and ownership combination; only one permits capture.
    for (mask in 0 until 1024) {
        val b=List(10){(mask and (1 shl it))!=0}
        val allowed=WakeRearmPolicy.eligible(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9])
        test(allowed == (mask==53))
    }
    val g=WakeRearmPolicy()
    test(!g.reserve(false,true,false));test(!g.reserve(true,false,false));test(!g.reserve(true,true,true))
    test(g.reserve(true,true,false));repeat(10){test(!g.reserve(true,true,false))}
    g.consume();test(g.reserve(true,true,false));g.cancel();test(g.reserve(true,true,false));g.consume()
    test(g.reserve(true,true,false));g.cancel();test(!g.reserve(false,true,false));test(g.reserve(true,true,false));g.consume()
    // A periodic watchdog cannot steal the live owner's microphone.
    val lease=MicLease();val wake=Any();val manual=Any();val other=Any()
    test(lease.acquire(wake));test(!lease.acquire(manual));lease.release(other);test(lease.owns(wake));lease.release(wake)
    test(lease.acquire(manual));test(!lease.acquire(wake));lease.release(wake);test(lease.owns(manual));lease.release(manual);test(lease.acquire(wake));lease.release(wake)
    test(WakeRetryPolicy.delayMs(1)==1000L);test(WakeRetryPolicy.delayMs(6)==32000L)
    println("$count/$count eligibility, rearm scheduling, lease and backoff checks passed (pure JVM)")
}
