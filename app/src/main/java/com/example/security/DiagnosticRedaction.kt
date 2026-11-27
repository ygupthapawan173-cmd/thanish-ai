package com.example.security

internal object DiagnosticRedaction {
    private val secret = Regex("(?i)(?:github_pat_[A-Za-z0-9_]{16,}|gh[pousr]_[A-Za-z0-9]{16,}|AIza[0-9A-Za-z_-]{25,}|sk-[A-Za-z0-9_-]{16,}|Bearer\\s+[^\\s,;]+)")
    fun clean(text: String): String = secret.replace(text, "[REDACTED]")
}
