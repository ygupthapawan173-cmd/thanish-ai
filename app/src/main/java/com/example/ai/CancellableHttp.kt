package com.example.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/** Cancellation cancels the transport even while the response body is being read. */
internal suspend fun <T> Call.awaitParsed(parse: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
        override fun onResponse(call: Call, response: Response) {
            val result = runCatching { response.use { r ->
                if (!continuation.isActive) return
                parse(r)
            } }
            if (continuation.isActive) continuation.resumeWith(result)
        }
    })
}
