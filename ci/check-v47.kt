import com.example.voice.TtsChunkPolicy
fun main() {
    val cases = listOf("", "hello", "a".repeat(10001), "తెలుగు మాటలు ".repeat(500), "😀🤖 hello 😀".repeat(500), "abc\nxyz ".repeat(1000))
    var checks=0
    for (text in cases) for (limit in listOf(2,3,17,4000)) {
        val chunks=TtsChunkPolicy.split(text,limit)
        check(chunks.joinToString("")==text)
        check(chunks.all { it.isNotEmpty() && it.length<=limit })
        check(chunks.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
        checks+=3
    }
    check(runCatching { TtsChunkPolicy.split("hi",1) }.isFailure); checks++
    println("PASS: $checks TTS chunk invariants")
}
