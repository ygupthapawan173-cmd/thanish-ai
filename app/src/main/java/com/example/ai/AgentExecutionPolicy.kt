package com.example.ai

/** Bounds provider-generated work; not an authorization decision. */
object AgentExecutionPolicy {
    const val MAX_CALLS_PER_HOP = 8
    const val MAX_CALLS_PER_REQUEST = 24
    const val MAX_ROUTINE_STEPS = 24
    fun batchAllowed(already: Int, incoming: Int): Boolean =
        already in 0..MAX_CALLS_PER_REQUEST && incoming in 1..MAX_CALLS_PER_HOP &&
            incoming <= MAX_CALLS_PER_REQUEST - already
    fun routineAllowed(count: Int): Boolean = count in 1..MAX_ROUTINE_STEPS
    fun validRoutineTool(name: String): Boolean = name.isNotBlank() && name != "run_routine"
    fun boundedContext(text: String, limit: Int): String {
        if (limit <= 0) return ""
        if (text.length <= limit) return text
        val end = if (Character.isHighSurrogate(text[limit - 1])) limit - 1 else limit
        return text.substring(0, end)
    }
}
