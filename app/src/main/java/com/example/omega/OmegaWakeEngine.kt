package com.example.omega

enum class WakeSensitivity(val label: String) {
    STRICT("Strict (recommended)"),
    BALANCED("Balanced"),
    SENSITIVE("Sensitive")
}
data class WakeMatch(val phrase: String, val tail: String, val distance: Int)

object OmegaWakeEngine {
    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(current[j-1]+1, prev[j]+1, prev[j-1]+if (a[i-1] == b[j-1]) 0 else 1)
            }
            val swap = prev; prev = current; current = swap
        }
        return prev[b.length]
    }

    fun validCustomPhrase(raw: String): String? {
        val words = OmegaPolicy.normalize(raw).split(' ').filter { it.isNotEmpty() }
        if (words.size !in 2..4 || words.any { it.length < 2 }) return null
        val phrase = words.joinToString(" ")
        return if (phrase.length in 7..30) phrase else null
    }

    private fun allowedEdits(word: String, mode: WakeSensitivity) = when(mode) {
        WakeSensitivity.STRICT -> 0
        WakeSensitivity.BALANCED -> if (word.length >= 7) 1 else 0
        WakeSensitivity.SENSITIVE -> if (word.length >= 6) 1 else 0
    }

    fun match(text: String, phrases: List<String>, sensitivity: WakeSensitivity = WakeSensitivity.STRICT, maxTail: Int = 300): WakeMatch? {
        val words = OmegaPolicy.normalize(text).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        var best: WakeMatch? = null
        var bestCount = 0
        for (phrase in phrases) {
            val pieces = OmegaPolicy.normalize(phrase).split(' ').filter { it.isNotEmpty() }
            if (pieces.isEmpty() || words.size < pieces.size) continue
            var edits = 0
            var accepted = true
            for (i in pieces.indices) {
                val n = editDistance(words[i], pieces[i])
                if (n > allowedEdits(pieces[i], sensitivity)) { accepted = false; break }
                edits += n
            }
            if (!accepted) continue
            if (best == null || edits < best.distance || edits == best.distance && pieces.size > bestCount) {
                best = WakeMatch(phrase, words.drop(pieces.size).joinToString(" ").take(maxTail), edits)
                bestCount = pieces.size
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
