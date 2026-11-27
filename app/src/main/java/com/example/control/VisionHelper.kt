package com.example.control

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object VisionHelper {
    fun bitmapToBase64Jpeg(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 78): String {
        require(!bitmap.isRecycled) { "Image is no longer available" }
        val (width, height) = VisionEncodingPolicy.dimensions(bitmap.width, bitmap.height, maxDimension)
        val scaled = if (width != bitmap.width || height != bitmap.height) {
            Bitmap.createScaledBitmap(
                bitmap,
                width,
                height,
                true
            )
        } else bitmap

        val out = ByteArrayOutputStream()
        try {
            check(scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1,100), out)) { "Image encoding failed" }
            require(VisionEncodingPolicy.acceptsSize(out.size())) { "Encoded image exceeds safe size; reduce image quality" }
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }
}
