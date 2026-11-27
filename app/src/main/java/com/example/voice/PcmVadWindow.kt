package com.example.voice

/** 16 kHz mono PCM16 little-endian -> stateful 512-sample windows with 64 samples of context. */
internal class PcmVadWindow {
    private val frame = FloatArray(512)
    private val context = FloatArray(64)
    private var used = 0
    private var lowByte = -1
    fun feed(bytes: ByteArray, length: Int, consume: (FloatArray) -> Unit) {
        require(length in 0..bytes.size)
        for (i in 0 until length) {
            val b = bytes[i].toInt() and 255
            if (lowByte < 0) { lowByte = b; continue }
            frame[used++] = ((b shl 8) or lowByte).toShort() / 32768f
            lowByte = -1
            if (used == frame.size) {
                val input = FloatArray(576)
                context.copyInto(input)
                frame.copyInto(input, 64)
                frame.copyInto(context, 0, 448, 512)
                used = 0
                consume(input)
            }
        }
    }
}
