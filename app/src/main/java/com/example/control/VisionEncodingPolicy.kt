package com.example.control

internal object VisionEncodingPolicy {
    const val MAX_JPEG_BYTES = 4 * 1024 * 1024
    fun dimensions(width: Int, height: Int, maximum: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, maximum.coerceIn(128, 2048).toDouble() / maxOf(width, height))
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
    fun acceptsSize(size: Int): Boolean = size in 1..MAX_JPEG_BYTES
}
