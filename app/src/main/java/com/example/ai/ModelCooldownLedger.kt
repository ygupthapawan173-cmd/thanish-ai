package com.example.ai

/** Bounded, per-credential ledger. At capacity fail closed until the earliest expiry. */
internal class ModelCooldownLedger(private val limit: Int = 512) {
    init { require(limit > 0) }
    private val deadlines = mutableMapOf<Pair<String,String>,Long>()
    private var overflowUntil = 0L
    private fun prune(now:Long) { deadlines.entries.removeIf { it.value <= now } }
    @Synchronized fun blocked(scope:String, model:String, now:Long):Boolean {
        prune(now)
        return now < overflowUntil || (deadlines[scope to model] ?: 0L) > now
    }
    @Synchronized fun mark(scope:String, model:String, now:Long, delay:Long) {
        if (now < 0 || delay <= 0) return
        prune(now)
        val end = now + delay.coerceAtMost(Long.MAX_VALUE-now)
        val key = scope to model
        if (key in deadlines || deadlines.size < limit) deadlines[key] = maxOf(end,deadlines[key] ?: 0L)
        else overflowUntil = maxOf(overflowUntil,end)
    }
}
