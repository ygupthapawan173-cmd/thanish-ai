package com.example.autonomous

object SelectedDependencyAnalysis {
    private val pkg = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)")
    private val imports = Regex("(?m)^\\s*import\\s+([A-Za-z_][A-Za-z0-9_.*]*)")
    private val declarations = Regex("(?m)^\\s*(?:(?:public|internal|private|protected|data|sealed|enum|abstract|open|value|annotation|suspend|inline|override|operator|actual|expect|fun)\\s+)*(?:class|interface|object|fun|typealias)\\s+([A-Za-z_][A-Za-z0-9_]*)")
    data class Module(val path: String, val packageName: String, val imports: Set<String>, val symbols: Set<String>)
    fun analyze(path: String, source: String): Module = Module(path, pkg.find(source)?.groupValues?.get(1).orEmpty(), imports.findAll(source).map { it.groupValues[1] }.take(128).toSet(), declarations.findAll(source).map { it.groupValues[1] }.take(128).toSet())
    private fun related(main: Module, peer: Module): Boolean = main.path != peer.path && peer.packageName.isNotEmpty() && (
        main.imports.any { it == peer.packageName + ".*" || (it.startsWith(peer.packageName + ".") && it.removePrefix(peer.packageName + ".").substringBefore('.') in peer.symbols) } || main.packageName == peer.packageName)
    fun reachable(target: String, sources: Map<String, String>): Set<String> {
        val modules = sources.map { (path, source) -> analyze(path, source) }.associateBy { it.path }
        if (target !in modules) return emptySet()
        val seen = linkedSetOf(target); val queue = ArrayDeque<String>(); queue.add(target)
        while (queue.isNotEmpty()) {
            val current = modules.getValue(queue.removeFirst())
            modules.values.filter { related(current, it) }.forEach { if (seen.add(it.path)) queue.add(it.path) }
        }
        seen.remove(target)
        return seen
    }
    fun describe(target: String, sources: Map<String, String>): String {
        val modules = sources.map { (path, source) -> analyze(path, source) }
        val main = modules.firstOrNull { it.path == target } ?: return "No selected dependency context"
        val reachable = reachable(target, sources)
        return modules.filter { it.path != target }.joinToString("\n") { peer ->
            "${peer.path}: package=${peer.packageName}; possible dependency=${related(main,peer)}; transitively reachable=${peer.path in reachable}; declarations=${peer.symbols.joinToString()}"
        }.take(6000)
    }
}
