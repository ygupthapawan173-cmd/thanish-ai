import com.example.data.*
import kotlin.concurrent.thread

private class FakeDisk : ApiTelemetryStore {
    var stored = ApiTelemetrySnapshot()
    var fail = false
    override fun load() = stored.copy(persistenceHealthy = true)
    override fun save(snapshot: ApiTelemetrySnapshot): Boolean {
        if (fail) return false
        stored = snapshot
        return true
    }
}
fun main() {
    var passed = 0
    fun test(name:String, condition:Boolean) { check(condition) { name }; passed++; println("PASS: $name") }
    val disk = FakeDisk()
    ApiQuotaTracker.attach(disk)
    ApiQuotaTracker.recordSuccess("free-model", 20, 40, 100, 0.0)
    ApiQuotaTracker.recordFailure()
    test("persistent request count", disk.stored.totalRequests == 2)
    test("persistent token count", disk.stored.reportedOutputTokens == 40L)
    ApiQuotaTracker.attach(disk)
    test("reload retains usage", ApiQuotaTracker.snapshot.value.totalRequests == 2)
    test("reload retains model", ApiQuotaTracker.snapshot.value.lastUsedModel == "free-model")
    test("reload retains failures", ApiQuotaTracker.snapshot.value.failedRequests == 1)
    ApiQuotaTracker.recordSuccess("unknown", null, null, 200)
    test("unknown cost tracked", disk.stored.requestsWithoutCost == 1)
    test("latency excludes failures", disk.stored.averageLatencyMs == 150L)
    disk.fail = true
    ApiQuotaTracker.recordFailure()
    test("write failure exposed", !ApiQuotaTracker.snapshot.value.persistenceHealthy)
    test("write failure leaves live count", ApiQuotaTracker.snapshot.value.totalRequests == 4)
    disk.fail = false
    val jobs = (1..4).map { thread { repeat(100) { ApiQuotaTracker.recordSuccess("x", 1, 1, 10, 0.0) } } }
    jobs.forEach { it.join() }
    test("concurrent persistent counts", disk.stored.totalRequests == 404)
    test("recovery persists live counters", ApiQuotaTracker.snapshot.value.persistenceHealthy)
    ApiQuotaTracker.reset()
    test("reset persists", disk.stored.totalRequests == 0)
    println("$passed/12 telemetry checks passed (in-memory storage test double)")
}
