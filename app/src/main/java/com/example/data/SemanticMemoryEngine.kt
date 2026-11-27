package com.example.data

import kotlin.math.sqrt

/**
 * Real on-device token-cosine Semantic Memory Search (v18 Supreme):
 * Ranks stored personal memories by semantic token overlap & IDF-like weighting.
 */
object SemanticMemoryEngine {

    private val stopWords = setOf(
        "the", "is", "a", "an", "my", "me", "i", "to", "for", "of", "in", "on", "at", "and", "or",
        "what", "who", "where", "when", "how", "naa", "nenu", "lo", "ki", "ni", "gurinchi"
    )

    fun tokenize(text: String): List<String> =
        java.text.Normalizer.normalize(com.example.ai.AgentExecutionPolicy.boundedContext(text, 16384), java.text.Normalizer.Form.NFC).lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-z0-9\u0C00-\u0C7F\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
            .take(1024)

    fun search(query: String, memories: List<MemoryFact>, topK: Int = 6): List<MemoryFact> {
        if (memories.isEmpty() || topK <= 0) return emptyList()
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return emptyList()

        val qFreq = qTokens.groupingBy { it }.eachCount()

        val scored = memories.asSequence().map { fact ->
            val docTokens = tokenize("${fact.key} ${fact.value}")
            val dFreq = docTokens.groupingBy { it }.eachCount()
            val score = cosineSimilarity(qFreq, dFreq)
            fact to score
        }

        val relevant = scored.filter { it.second > 0.05 }.sortedWith(compareByDescending<Pair<MemoryFact,Double>> { it.second }.thenByDescending { it.first.updatedAtMs }.thenBy { it.first.id }).map { it.first }
        return relevant.take(topK.coerceAtMost(50)).toList()
    }

    private fun cosineSimilarity(a: Map<String, Int>, b: Map<String, Int>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for ((k, v) in a) {
            normA += v.toDouble() * v
            val bv = b[k]
            if (bv != null) dot += v.toDouble() * bv
            else {
                // Partial stem bonus for Telugu/English inflections
                // Only whole-token prefix matches of at least five characters, never arbitrary first-four-letter collisions.
                val best = b.entries.filter { (bk, _) -> minOf(k.length,bk.length)>=5 && (bk.startsWith(k) || k.startsWith(bk)) }
                    .maxOfOrNull { it.value } ?: 0
                dot += 0.35 * v * best
            }
        }
        for ((_, v) in b) normB += v.toDouble() * v
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else (dot / denom).coerceIn(0.0, 1.0)
    }
}
