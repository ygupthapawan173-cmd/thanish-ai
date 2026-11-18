package com.example.ai

import android.content.Context

/**
 * Small, local-only memory store. It saves facts only when the user explicitly asks ZENO
 * to remember them; ordinary chat is never silently promoted into long-term memory.
 */
class ZenoMemoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("zeno_memory_v1", Context.MODE_PRIVATE)

    fun promptContext(): String = preferences.getStringSet(KEY_MEMORIES, emptySet())
        .orEmpty()
        .sorted()
        .joinToString("\n")
        .take(MAX_CONTEXT_CHARS)

    /** Returns a user-facing reply when the prompt is a memory command, otherwise null. */
    fun handleCommand(rawPrompt: String): String? {
        val prompt = rawPrompt.trim()
        val normalized = prompt.lowercase()

        if (normalized in CLEAR_COMMANDS || prompt in CLEAR_TELUGU_COMMANDS) {
            preferences.edit().remove(KEY_MEMORIES).apply()
            return "సరే బాస్. ZENO memory లో సేవ్ చేసిన విషయాలన్నీ క్లియర్ చేశాను."
        }

        val forgetPrefix = FORGET_PREFIXES.firstOrNull { normalized.startsWith(it) }
        if (forgetPrefix != null) {
            val item = prompt.drop(forgetPrefix.length).trim().trim(':', '-', ' ')
            if (item.isBlank()) return "ఏ విషయాన్ని మర్చిపోవాలో చెప్పు బాస్. ఉదాహరణకు: Forget: నా ఫేవరెట్ రంగు నీలం."
            val updated = preferences.getStringSet(KEY_MEMORIES, emptySet()).orEmpty().toMutableSet()
            val removed = updated.removeAll { it.equals(item, ignoreCase = true) || it.contains(item, ignoreCase = true) }
            preferences.edit().putStringSet(KEY_MEMORIES, updated).apply()
            return if (removed) "సరే బాస్. ఆ విషయాన్ని ZENO memory నుంచి తీసేశాను."
            else "ఆ విషయం ZENO memory లో కనిపించలేదు బాస్."
        }

        val rememberPrefix = REMEMBER_PREFIXES.firstOrNull { normalized.startsWith(it) }
            ?: REMEMBER_TELUGU_PREFIXES.firstOrNull { prompt.startsWith(it) }
            ?: return null
        val item = prompt.drop(rememberPrefix.length).trim().trim(':', '-', ' ')
        if (item.isBlank()) return "ఏ విషయాన్ని గుర్తుపెట్టుకోవాలో చెప్పు బాస్. ఉదాహరణకు: Remember: నాకు తెలుగు సమాధానాలు ఇష్టం."
        if (item.length > MAX_ITEM_CHARS) return "ఆ memory చాలా పెద్దగా ఉంది బాస్. దయచేసి 240 అక్షరాల్లో చెప్పు."
        if (SENSITIVE_TERMS.any { item.contains(it, ignoreCase = true) }) {
            return "భద్రత కోసం passwords, OTPలు, PINలు లేదా API keys ను ZENO memory లో సేవ్ చేయను బాస్."
        }
        val memories = preferences.getStringSet(KEY_MEMORIES, emptySet()).orEmpty().toMutableSet()
        memories.removeAll { it.equals(item, ignoreCase = true) }
        if (memories.size >= MAX_ITEMS) memories.remove(memories.first())
        memories.add(item)
        preferences.edit().putStringSet(KEY_MEMORIES, memories).apply()
        return "గుర్తుపెట్టుకున్నాను బాస్. తర్వాతి సమాధానాల్లో అవసరమైనప్పుడు దీన్ని ఉపయోగిస్తాను."
    }

    private companion object {
        const val KEY_MEMORIES = "approved_memories"
        const val MAX_ITEMS = 40
        const val MAX_ITEM_CHARS = 240
        const val MAX_CONTEXT_CHARS = 4_000
        val REMEMBER_PREFIXES = listOf("remember this:", "remember:", "learn this:", "remember this ", "remember ")
        val FORGET_PREFIXES = listOf("forget:", "forget this:", "forget ")
        val REMEMBER_TELUGU_PREFIXES = listOf("గుర్తుపెట్టుకో:", "ఇది గుర్తుపెట్టుకో:", "గుర్తుపెట్టుకో ")
        val CLEAR_COMMANDS = setOf("forget all memory", "clear memory", "clear zeno memory", "forget everything")
        val CLEAR_TELUGU_COMMANDS = setOf("మెమరీ మొత్తం మర్చిపో", "అన్ని మెమరీలు క్లియర్ చెయ్", "జ్ఞాపకాలు అన్నీ తొలగించు")
        val SENSITIVE_TERMS = listOf("password", "passcode", "otp", "pin number", "api key", "secret key", "పాస్వర్డ్", "ఓటిపి", "పిన్ నంబర్")
    }
}
