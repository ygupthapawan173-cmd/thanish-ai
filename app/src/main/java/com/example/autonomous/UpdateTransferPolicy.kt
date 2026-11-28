package com.example.autonomous

internal object UpdateTransferPolicy {
    const val MAX_APK_BYTES = 512L * 1024 * 1024
    fun validRepo(repo: String): Boolean = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo.trim())
    fun validHash(hash: String?) = hash != null && Regex("[0-9a-fA-F]{64}").matches(hash)
    fun allowedAsset(url: String): Boolean = runCatching {
        val u = java.net.URI(url)
        u.scheme.equals("https", true) && u.host.equals("github.com", true) && u.userInfo == null && u.query == null && u.fragment == null && u.normalize().path == u.path && !Regex("(?i)%2f|%5c|%2e|%25").containsMatchIn(u.rawPath.orEmpty()) && (u.port == -1 || u.port == 443) &&
            Regex("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/.+").matches(u.path.orEmpty())
    }.getOrDefault(false)

    fun belongsTo(url:String, repo:String, tag:String, asset:String):Boolean {
        if (!allowedAsset(url) || repo.split('/').size != 2 || tag.isBlank() || asset.isBlank() || '/' in tag || '/' in asset || '\\' in asset) return false
        val path=java.net.URI(url).path
        val prefix="/$repo/releases/download/"
        return path.regionMatches(0,prefix,0,prefix.length,ignoreCase=true) && path.substring(prefix.length)=="$tag/$asset"
    }
    fun checksumFor(text:String, apk:String, allowBare:Boolean):String? {
        if (text.length>65536) return null
        val bare=text.trim()
        if (allowBare && validHash(bare)) return bare.lowercase(java.util.Locale.ROOT)
        val pattern=Regex("^([0-9a-fA-F]{64})[ \t]+\\*?(.+)$")
        val matches=text.lineSequence().mapNotNull { pattern.matchEntire(it.trim()) }.filter { it.groupValues[2] in setOf(apk,"./$apk","out/$apk","release-out/$apk") }.map { it.groupValues[1].lowercase(java.util.Locale.ROOT) }.toSet()
        return matches.singleOrNull()
    }
}
