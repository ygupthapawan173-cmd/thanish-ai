package com.example

import com.example.ai.readGatewayStream
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class V107StreamingTest {
    private fun read(raw: String, parts: MutableList<String> = mutableListOf()): String {
        var now = 0L
        val r = Response.Builder().request(Request.Builder().url("https://example.com/").build()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(raw.toResponseBody()).build()
        return r.use { it.readGatewayStream("test/model", { value -> parts.add(value) }, { now += 100; now }).text }
    }
    private fun chunk(text: String) = "data: {\"choices\":[{\"delta\":{\"content\":\"$text\"}}]}\n\n"
    private val finish = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
    @Test fun incrementalTeluguEnglish() { val parts = mutableListOf<String>(); assertEquals("Hello నమస్తే", read(chunk("Hello ")+chunk("నమస్తే")+finish,parts)); assertTrue(parts.contains("Hello ")); assertEquals("Hello నమస్తే",parts.last()) }
    @Test(expected=IOException::class) fun truncatedConnection() { read(chunk("partial")) }
    @Test(expected=IOException::class) fun lengthRejected() { read(chunk("partial")+"data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n") }
    @Test(expected=IOException::class) fun errorRejected() { read("data: {\"error\":{\"message\":\"failed\"}}\n\n") }
    @Test(expected=IOException::class) fun doneWithoutFinishRejected() { read(chunk("partial")+"data: [DONE]\n\n") }
    @Test fun commentHeartbeat() { assertEquals("ok",read(": ping\n\n"+chunk("ok")+finish)) }
}
