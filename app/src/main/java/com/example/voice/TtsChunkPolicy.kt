package com.example.voice

/** Preserve every UTF-16 code unit, never split surrogate pairs. */
internal object TtsChunkPolicy {
    fun split(text: String, max: Int): List<String> {
        require(max >= 2)
        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = start + minOf(max, text.length - start)
            if (end < text.length) {
                if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
                val preferred = (end - 1 downTo (start + (end - start) / 2)).firstOrNull { text[it].isWhitespace() }
                if (preferred != null) end = preferred + 1
            }
            parts.add(text.substring(start, end)); start = end
        }
        return parts
    }
}
