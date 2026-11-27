package com.example.autonomous

internal object ReleaseVersionPolicy {
    private val version = Regex("(?i)^v?([0-9]{1,9})(?:\\.([0-9]{1,9}))?(?:\\.([0-9]{1,9}))?(?:[\\s\\-+][A-Za-z0-9._\\s-]+)?$")
    private fun parts(s: String): List<Int>? =
        version.matchEntire(s.trim())?.groupValues?.drop(1)?.map { if (it.isEmpty()) 0 else it.toInt() }

    fun newer(tag: String, installed: String): Boolean {
        val a = parts(tag) ?: return false
        val b = parts(installed) ?: return false
        for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
        return false // same numeric version: archive versionCode remains authoritative
    }
}
