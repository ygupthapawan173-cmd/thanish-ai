package com.example

import com.example.data.ApiQuotaTracker as Q
import org.junit.Assert.*
import org.junit.Test
import kotlin.concurrent.thread

class ApiQuotaTrackerRegressionTest {
    @Test fun actualUsageAndUnknownCost() {
        Q.reset()
        Q.recordSuccess("a", 100, 200, 100, 0.1)
        Q.recordFailure()
        Q.recordSuccess("b", null, null, 300)
        val s = Q.snapshot.value
        assertEquals(100L, s.reportedPromptTokens)
        assertEquals(200L, s.reportedOutputTokens)
        assertEquals(1, s.requestsWithoutUsage)
        assertEquals(1, s.requestsWithoutCost)
        assertEquals(0.1, s.knownCostUsd, 0.0000001)
        assertEquals(200L, s.averageLatencyMs)
        Q.reset()
    }
    @Test fun concurrentAccounting() {
        Q.reset()
        val threads = (1..10).map { thread {
            repeat(1000) { Q.recordSuccess("x", 1, 2, 10, 0.0) }
            repeat(100) { Q.recordFailure() }
        } }
        threads.forEach { it.join() }
        val s = Q.snapshot.value
        assertEquals(11000, s.totalRequests)
        assertEquals(1000, s.failedRequests)
        assertEquals(10000L, s.reportedPromptTokens)
        assertEquals(20000L, s.reportedOutputTokens)
        Q.reset()
    }
}
