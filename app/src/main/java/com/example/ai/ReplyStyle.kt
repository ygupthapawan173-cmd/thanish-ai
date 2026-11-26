package com.example.ai

import android.content.Context

enum class ReplyStyle(val label: String, val instruction: String, val maxTokens: Int) {
    SHORT("Short", "Style: answer in one or two short sentences unless more detail is requested.", 300),
    BALANCED("Balanced", "", 700),
    DETAILED("Detailed", "Style: answer thoroughly with steps and examples when useful.", 1400),
    TELUGU_ONLY("Telugu", "Style: reply in Telugu script; English only when necessary.", 900),
    TENGLISH("Tenglish", "Style: reply in Telugu written with English letters, kept clear and natural.", 600);

    fun systemWith(base: String): String = if (instruction.isBlank()) base else base + "\n" + instruction
}

object ReplyPreferences {
    fun get(c: Context): ReplyStyle = runCatching {
        ReplyStyle.valueOf(c.getSharedPreferences("omega_ai", 0).getString("reply_style", "BALANCED")!!)
    }.getOrDefault(ReplyStyle.BALANCED)

    fun set(c: Context, style: ReplyStyle) =
        c.getSharedPreferences("omega_ai", 0).edit().putString("reply_style", style.name).apply()
}

/** Recent history bounded by both item and character limits. Never exceeds the budget. */
object OmegaContext {
    fun <T> recent(items: List<T>, maxItems: Int, maxChars: Int, size: (T) -> Int): List<T> {
        if (maxItems <= 0 || maxChars <= 0) return emptyList()
        val out = ArrayList<T>()
        var total = 0
        for (i in items.indices.reversed()) {
            if (out.size >= maxItems) break
            val n = size(items[i])
            if (n < 0 || n > maxChars) continue
            if (total + n > maxChars) break
            out.add(items[i])
            total += n
        }
        out.reverse()
        return out
    }
}
