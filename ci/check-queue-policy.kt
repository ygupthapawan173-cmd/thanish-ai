import com.example.omega.QueueRunPolicy
fun main() {
    val day = "2026-11-27"
    val cases = listOf(
        QueueRunPolicy.allowed("a", "a", 1, 1, day, day, false),
        !QueueRunPolicy.allowed("a", "b", 1, 1, day, day, false),
        !QueueRunPolicy.allowed("a", null, 1, 1, day, day, false),
        !QueueRunPolicy.allowed("a", "a", 1, 2, day, day, false),
        !QueueRunPolicy.allowed("a", "a", 1, 1, day, "2026-11-28", false),
        !QueueRunPolicy.allowed("a", "a", 1, 1, day, day, true),
        !QueueRunPolicy.allowed("", "", 1, 1, day, day, false),
        !QueueRunPolicy.allowed("a", "a", -1, -1, day, day, false),
        !QueueRunPolicy.allowed("a", "a", 1, 1, "", "", false)
    )
    cases.forEachIndexed { i, result -> check(result) { "Queue policy case ${i+1}" } }
    println("PASS: ${cases.size}/${cases.size} persistent queue approval policy cases")
}
