package com.example.ai

import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

internal data class GatewayStreamResult(val text: String, val model: String, val usage: JSONObject?)
internal fun Response.readGatewayStream(defaultModel: String, onPartial: (String) -> Unit, clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }): GatewayStreamResult {
    val src = body?.source() ?: throw IOException("Empty streaming response")
    val text = StringBuilder()
    var model = defaultModel
    var usage: JSONObject? = null
    var terminal = false
    var done = false
    var bytes = 0L
    var lastEmit = 0L
    val event = StringBuilder()
    fun consume() {
        if (event.isEmpty()) return
        val data = event.toString().trim(); event.setLength(0)
        if (data == "[DONE]") { done = true; return }
        val json = JSONObject(data)
        if (json.has("error")) throw IOException("Provider stream returned an error")
        json.optString("model").takeIf { SmartModelPolicy.validId(it) }?.let { model = it }
        json.optJSONObject("usage")?.let { usage = it }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val delta = choice?.optJSONObject("delta")?.opt("content") as? String ?: ""
        if (text.length + delta.length > 65536) throw IOException("Streaming answer exceeds limit")
        text.append(delta)
        val now = clock()
        if (delta.isNotEmpty() && now - lastEmit >= 80) { onPartial(text.toString()); lastEmit = now }
        if (choice?.optString("finish_reason") == "length") throw IOException("Provider truncated streaming answer")
        if (choice?.optString("finish_reason") == "stop") terminal = true
    }
    while (true) {
        val line = try { src.readUtf8LineStrict(262144L) } catch (e: java.io.EOFException) {
            if (!src.exhausted()) throw IOException("Malformed or oversized stream line", e)
            consume(); break
        }
        bytes += line.toByteArray(Charsets.UTF_8).size + 1L
        if (bytes > 8L * 1024 * 1024) throw IOException("Stream exceeds limit")
        if (line.isEmpty()) { consume(); if (done) break }
        else if (line.startsWith("data:")) { if (event.length + line.length > 262144) throw IOException("Stream event exceeds limit"); if(event.isNotEmpty()) event.append('\n'); event.append(line.substring(5).trimStart()) }
    }
    if (!terminal || text.isBlank()) throw IOException("Incomplete streaming answer; no automatic replay")
    onPartial(text.toString())
    return GatewayStreamResult(text.toString().trim(), model, usage)
}
