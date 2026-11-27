package com.example.security

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashLogEntry(
    val timestamp: String,
    val threadName: String,
    val summary: String,
    val stackTrace: String
)

/**
 * Automatic Crash Capture & Analyzer (v18 Supreme):
 * Captures uncaught exceptions to local app storage so the Health & Crash Analyzer
 * can diagnose issues without external telemetry.
 */
object CrashRecoveryHandler {

    private const val LOG_FILE = "omega_crash_logs.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = buildString {
                    append("=== CRASH [$ts] Thread=${thread.name} ===\n")
                    append("${throwable.javaClass.simpleName}: ${throwable.message}\n")
                    append(throwable.stackTraceToString().take(2400))
                    append("\n\n")
                }
                val file = File(appContext.filesDir, LOG_FILE)
                val existing = if (file.exists()) file.readText().takeLast(12000) else ""
                file.writeText(DiagnosticRedaction.clean(existing + entry).takeLast(16000))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLogs(context: Context): String {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        return if (file.exists()) DiagnosticRedaction.clean(file.readText()).ifBlank { "No crashes recorded in this log." }
        else "No crashes recorded in this log."
    }

    fun clearLogs(context: Context) {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        if (file.exists()) file.delete()
    }
}
