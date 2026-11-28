package com.example

import com.example.ai.AgentExecutionPolicy as P

class V100AgentRegressionTest {
    @org.junit.Test
    fun policies() {
        var n = 0
        fun t(b: Boolean) { check(b) { "Agent policy case ${n + 1}" }; n++ }
        for (used in listOf(-1, 0, 7, 16, 17, 23, 24, 25, Int.MAX_VALUE)) {
            for (incoming in listOf(-1, 0, 1, 7, 8, 9, Int.MAX_VALUE)) {
                t(P.batchAllowed(used, incoming) == (used in 0..24 && incoming in 1..8 && used.toLong() + incoming <= 24))
            }
        }
        for (count in listOf(-1, 0, 1, 8, 23, 24, 25, Int.MAX_VALUE)) t(P.routineAllowed(count) == (count in 1..24))
        for (name in listOf("", " ", "run_routine", "set_media_volume", "open_app")) {
            t(P.validRoutineTool(name) == (name.isNotBlank() && name != "run_routine"))
        }
        t(P.boundedContext("abc", 0) == "")
        t(P.boundedContext("abc", 2) == "ab")
        t(P.boundedContext("abc", 3) == "abc")
        t(P.boundedContext("a😀b", 2) == "a")
        t(P.boundedContext("a😀b", 3) == "a😀")
        println("$n/$n agent policy checks passed")
    }
}
