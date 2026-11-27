package com.example.ai

import okhttp3.Response
import java.io.IOException

/** Bound decoded HTTP JSON (including gzip); reject rather than truncate. */
internal fun Response.boundedJsonText(maxBytes: Long = 8L * 1024 * 1024): String {
    require(maxBytes in 1..(32L * 1024 * 1024))
    val data = body ?: throw IOException("Empty provider response")
    if (data.contentLength() > maxBytes) throw IOException("Provider response exceeds safe size")
    val source = data.source()
    if (source.request(maxBytes + 1)) throw IOException("Provider response exceeds safe size")
    return source.readUtf8()
}
