package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SEARCH_TAG = "PanduSearch"
private const val WORKER_URL =
    "https://pandu-babu-search-2104.ygupthapawan173.workers.dev"

class PanduSearchRepository {

    suspend fun enrichPromptIfNeeded(prompt: String): String {
        if (!needsLiveSearch(prompt)) return prompt

        return try {
            val sourceNotes = withTimeout(6_000L) {
                withContext(Dispatchers.IO) { search(prompt) }
            }
            if (sourceNotes.isBlank()) prompt else """
                $prompt

                CURRENT WEB SEARCH RESULTS (use only as supporting sources; do not invent facts):
                $sourceNotes
                Answer briefly in natural Telugu. Mention source names and include relevant URLs.
            """.trimIndent()
        } catch (e: Exception) {
            Log.w(SEARCH_TAG, "Live search unavailable; continuing with AI only", e)
            prompt
        }
    }

    private fun search(query: String): String {
        val connection = (URL(WORKER_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            val body = JSONObject().put("query", query.take(500)).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) return ""

            val json = JSONObject(responseText)
            val parts = mutableListOf<String>()
            json.optString("answer").takeIf { it.isNotBlank() }?.let {
                parts += "Search summary: $it"
            }
            val results = json.optJSONArray("results")
            if (results != null) {
                for (i in 0 until minOf(results.length(), 4)) {
                    val item = results.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val url = item.optString("url")
                    val content = item.optString("content").take(500)
                    if (title.isNotBlank() || content.isNotBlank()) {
                        parts += "Source: $title\nURL: $url\nDetails: $content"
                    }
                }
            }
            parts.joinToString("\n\n")
        } finally {
            connection.disconnect()
        }
    }

    private fun needsLiveSearch(prompt: String): Boolean {
        val text = prompt.lowercase()
        val terms = listOf(
            "latest", "current", "today", "now", "news", "weather", "price",
            "rate", "score", "result", "update", "recent", "search", "who is",
            "when is", "2026", "ఇప్పుడు", "ఈరోజు", "నేడు", "తాజా", "వార్త",
            "ధర", "రేటు", "వాతావరణం", "సెర్చ్", "ఎప్పుడు", "ప్రస్తుతం",
            "లైవ్", "అప్‌డేట్", "అప్డేట్"
        )
        return terms.any { text.contains(it) }
    }
}
