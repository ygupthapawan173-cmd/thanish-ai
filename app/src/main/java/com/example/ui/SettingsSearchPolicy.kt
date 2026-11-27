package com.example.ui

internal object SettingsSearchPolicy {
    const val MAX_QUERY = 256
    fun matches(query: String, title: String, description: String, aliases: String): Boolean {
        val text = "$title $description $aliases"
        return query.take(MAX_QUERY).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.all { text.contains(it, ignoreCase = true) }
    }
}
