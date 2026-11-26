package com.example.omega

enum class WakeSensitivity(val label: String) {
    STRICT("Strict · exact words only"),
    BALANCED("Balanced · 1 edit only for long words"),
    SENSITIVE("Sensitive · 1 edit for words 6+ letters")
}

data class WakeMatch(val phrase: String, val tail: String, val distance: Int)

object OmegaWakeEngine {
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev; prev = cur; cur = swap
        }
        return prev[b.length]
    }

    fun validCustomPhrase(raw: String): String? {
        val words = OmegaPolicy.normalize(raw).split(' ').filter { it.isNotEmpty() }
        if (words.size !in 2..4 || words.any { it.length < 2 }) return null
        val joined = words.joinToString(" ")
        return if (joined.length in 7..30) joined else null
    }

    private fun allowedEdits(word: String, sensitivity: WakeSensitivity): Int = when (sensitivity) {
        WakeSensitivity.STRICT -> 0
        WakeSensitivity.BALANCED -> if (word.length >= 7) 1 else 0
        WakeSensitivity.SENSITIVE -> if (word.length >= 6) 1 else 0
    }

    fun match(
        text: String,
        phrases: List<String>,
        sensitivity: WakeSensitivity = WakeSensitivity.STRICT,
        maxTail: Int = 300
    ): WakeMatch? {
        val words = OmegaPolicy.normalize(text).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        var best: WakeMatch? = null
        var bestWords = 0
        for (phrase in phrases) {
            val pw = OmegaPolicy.normalize(phrase).split(' ').filter { it.isNotEmpty() }
            if (pw.isEmpty() || words.size < pw.size) continue
            var total = 0
            var ok = true
            for (i in pw.indices) {
                val d = editDistance(words[i], pw[i])
                if (d > allowedEdits(pw[i], sensitivity)) { ok = false; break }
                total += d
            }
            if (!ok) continue
            val current = best
            if (current == null || total < current.distance ||
                (total == current.distance && pw.size > bestWords)) {
                best = WakeMatch(phrase, words.drop(pw.size).joinToString(" ").take(maxTail), total)
                bestWords = pw.size
            }
        }
        return best
    }
}

class WakeGate(private val cooldownMs: Long) {
    private var last = Long.MIN_VALUE
    fun allow(nowMs: Long): Boolean {
        if (last != Long.MIN_VALUE && nowMs - last < cooldownMs) return false
        last = nowMs
        return true
    }
    fun reset() { last = Long.MIN_VALUE }
}
