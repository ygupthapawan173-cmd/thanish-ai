package com.example.voice
fun main() {
    var n=0
    fun verify(c:Boolean){check(c);n++}
    for(p in HandsFreeWakePolicy.phrases) {
        val size=p.split(" ").size
        verify(HandsFreeWakePolicy.accept(p,List(size){0.95},0.7))
        verify(HandsFreeWakePolicy.accept(p.uppercase(),List(size){0.95},0.7))
        verify(!HandsFreeWakePolicy.accept(p,List(size){0.5},0.7))
        verify(!HandsFreeWakePolicy.accept(p,List(size){Double.NaN},0.7))
        verify(!HandsFreeWakePolicy.accept(p,List(size-1){0.95},0.7))
        verify(!HandsFreeWakePolicy.accept("$p open whatsapp",List(size+2){0.95},0.7))
    }
    for(p in listOf("jarvis","hello","", "[unk]", "hey siri", "hey jarvis stop")) verify(!HandsFreeWakePolicy.accept(p,listOf(0.99,0.99),0.7))
    for(x in -2..20) verify(HandsFreeWakePolicy.retryDelay(x) in 1000L..10000L)
    verify(HandsFreeWakePolicy.accept(" Hey   Jarvis ",listOf(0.8,0.8),0.7))
    println("$n/$n hands-free policy assertions passed")
}
