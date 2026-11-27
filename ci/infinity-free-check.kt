package com.example.ai
import com.example.ai.SmartModelPolicy.Model
import com.example.ai.SmartModelPolicy.Task
import com.example.ai.SmartModelPolicy.Mode
fun main() {
    val free=Model("vendor/zero:free",true,true,0.0,0.0)
    val paid=Model("vendor/paid",true,true,0.01,0.01)
    val unknown=Model("vendor/unknown:free",true,true,null,0.0)
    val deceptive=Model("vendor/billed:free",true,true,0.0,0.1)
    val notFreeId=Model("vendor/zero",true,true,0.0,0.0)
    val all=listOf(free,paid,unknown,deceptive,notFreeId)
    check(SmartModelPolicy.rank(all,Task.CHAT,Mode.FREE,"")==listOf(free))
    check(SmartModelPolicy.rank(all,Task.CODE,Mode.FREE,paid.id)==listOf(free))
    check(SmartModelPolicy.rank(all,Task.REASONING,Mode.FREE,"")==listOf(free))
    check(SmartModelPolicy.rank(all,Task.VISION,Mode.FREE,"")==listOf(free))
    check(SmartModelPolicy.rank(listOf(free.copy(imageInput=false)),Task.VISION,Mode.FREE,"").isEmpty())
    check(SmartModelPolicy.rank(all.drop(1),Task.CHAT,Mode.FREE,"").isEmpty())
    check(SmartModelPolicy.rank(all,Task.CHAT,Mode.MANUAL,paid.id)==listOf(paid))
    check(!SmartModelPolicy.isVerifiedFree(unknown))
    println("8/8 strict free model policy checks passed")
}
