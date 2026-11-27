package com.example.ai
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
class FakeCall : Call {
    lateinit var callback: Callback
    var cancelled=false
    override fun cancel(){cancelled=true}
    override fun enqueue(callback: Callback){this.callback=callback}
}
fun main() = runBlocking {
    val a=FakeCall(); val pending=async(start=CoroutineStart.UNDISPATCHED){ a.awaitParsed { 7 } }
    pending.cancelAndJoin(); check(a.cancelled); val late=Response(); a.callback.onResponse(a,late); check(late.closed)
    println("PASS: cancelled request cancels transport and closes late response")
    val b=FakeCall(); val completed=async(start=CoroutineStart.UNDISPATCHED){ b.awaitParsed { 42 } }
    val normal=Response(); b.callback.onResponse(b,normal); check(completed.await()==42 && normal.closed)
    println("PASS: successful parse returns value and closes response")
    val c=FakeCall(); val failure=async(start=CoroutineStart.UNDISPATCHED){ runCatching{ c.awaitParsed<Int> { error("invalid response") } } }
    val bad=Response(); c.callback.onResponse(c,bad); check(failure.await().isFailure && bad.closed)
    println("PASS: parse failure propagates and closes response")
    val d=FakeCall(); val network=async(start=CoroutineStart.UNDISPATCHED){ runCatching{ d.awaitParsed{ 1 } } }
    d.callback.onFailure(d,IOException("network error")); check(network.await().exceptionOrNull() is IOException)
    println("PASS: network failure propagates")
    println("4/4 cancellation checks passed (test doubles, not actual OkHttp network)")
}
