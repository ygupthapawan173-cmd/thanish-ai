import com.example.autonomous.EngineeringPolicy
fun main() {
    val original = "package com.example\nclass A"
    check(EngineeringPolicy.samePackage(original, "package com.example\nclass B"))
    check(!EngineeringPolicy.samePackage(original, "package com.other\nclass B"))
    check(!EngineeringPolicy.samePackage(original, "class B"))
    check(!EngineeringPolicy.samePackage("class A", "class B"))
    println("4/4 package identity checks passed")
}
