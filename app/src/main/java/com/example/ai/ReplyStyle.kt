package com.example.ai

/** User-selectable AI reply style. BALANCED adds nothing, so it matches the original behaviour. */
enum class ReplyStyle(val label: String, val instruction: String, val maxTokens: Int) {
    SHORT("Short · 1-2 sentences", "Style: answer in one or two short sentences unless the user asks for detail.", 300),
    BALANCED("Balanced (default)", "", 700),
    DETAILED("Detailed · step by step", "Style: give thorough, well-structured answers with steps or examples when useful.", 1400),
    TELUGU_ONLY("Telugu only", "Style: reply only in Telugu script; avoid English words unless there is no common Telugu word.", 900),
    TENGLISH("Tenglish · Telugu in English letters", "Style: reply in natural, casual Tenglish (Telugu written in English letters), kept short.", 600);

    fun systemWith(base: String): String = if (instruction.isBlank()) base else base + "\n" + instruction
}

/** Keeps the most recent items that fit a character budget (Telugu text costs more tokens than English). */
object OmegaContext {
    fun <T> recent(items: List<T>, maxItems: Int, maxChars: Int, size: (T) -> Int): List<T> {
        if (maxItems <= 0 || maxChars <= 0) return emptyList()
        val out = ArrayList<T>()
        var total = 0
        for (i in items.indices.reversed()) {
            if (out.size >= maxItems) break
            val n = size(items[i])
            if (n > maxChars) continue
            if (total + n > maxChars) break
            out.add(items[i])
            total += n
        }
        out.reverse()
        return out
    }
}
