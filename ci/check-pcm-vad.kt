package com.example.voice
fun main() {
    var checks = 0
    fun verify(value:Boolean) { check(value); checks++ }
    val data = ByteArray(2048) { i -> if (i % 2 == 0) 0 else if (i < 1024) 64 else -64 }
    val window = PcmVadWindow(); val outputs = mutableListOf<FloatArray>()
    window.feed(data.copyOfRange(0,1),1) { outputs.add(it) }
    verify(outputs.isEmpty())
    window.feed(data.copyOfRange(1,1023),1022) { outputs.add(it) }
    verify(outputs.isEmpty())
    window.feed(data.copyOfRange(1023,1024),1) { outputs.add(it) }
    verify(outputs.size == 1)
    verify(outputs[0].size == 576)
    verify(outputs[0].take(64).all { it == 0f })
    verify(outputs[0].drop(64).all { it == 0.5f })
    window.feed(data.copyOfRange(1024,2048),1024) { outputs.add(it) }
    verify(outputs.size == 2)
    verify(outputs[1].take(64).all { it == 0.5f })
    verify(outputs[1].drop(64).all { it == -0.5f })
    verify(runCatching { window.feed(data, data.size+1) {} }.isFailure)
    println("PASS: $checks/10 PCM window checks; no ONNX inference or Android execution")
}
