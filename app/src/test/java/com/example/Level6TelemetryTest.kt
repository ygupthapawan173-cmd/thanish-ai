package com.example
import com.example.data.*
import org.junit.Test
import org.junit.Assert.*
class Level6TelemetryTest {
    @Test fun persistedReloadAndFailure() {
        var saved = ApiTelemetrySnapshot()
        var writable = true
        val disk = object : ApiTelemetryStore {
            override fun load() = saved
            override fun save(snapshot: ApiTelemetrySnapshot): Boolean {
                if (writable) saved = snapshot
                return writable
            }
        }
        try {
            ApiQuotaTracker.attach(disk)
            ApiQuotaTracker.recordSuccess("test", 4, 8, 100, 0.0)
            ApiQuotaTracker.attach(disk)
            assertEquals(1, ApiQuotaTracker.snapshot.value.totalRequests)
            assertEquals(8L, ApiQuotaTracker.snapshot.value.reportedOutputTokens)
            writable = false
            ApiQuotaTracker.recordFailure()
            assertFalse(ApiQuotaTracker.snapshot.value.persistenceHealthy)
            assertEquals(2, ApiQuotaTracker.snapshot.value.totalRequests)
        } finally { writable = true; ApiQuotaTracker.reset() }
    }
}
