package com.example.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Generates review text only. Never commits code or executes generated instructions. */
object SelfEvolutionCompilerLoop {
    suspend fun generateDraft(context:Context,path:String,source:String,request:String):String {
        require(validPath(path) && source.isNotBlank() && source.length<=24000 && request.length in 1..2000)
        val prompt="Create a reviewable Kotlin change proposal for "+path+
            ". Return a concise explanation and code patch. No credentials or autonomous execution. Existing source:\n"+source+"\nRequested change:\n"+request
        var answer=""
        PanduAiRepository(context).streamReply(emptyList(),prompt).collect { if(it is AiStreamEvent.Completed) answer=it.fullText }
        check(answer.isNotBlank())
        return answer
    }
    fun validPath(path:String)=path.startsWith("app/src/main/java/com/example/") && path.endsWith(".kt") &&
        !path.contains("..") && path.matches(Regex("[A-Za-z0-9_./-]{1,180}"))
    fun validDigest(hash:String)=hash.matches(Regex("[a-fA-F0-9]{64}"))
    data class VerifiedUpdate(val file:File,val digest:String,val version:Long)
    @Suppress("DEPRECATION")
    suspend fun verifyUpdate(context:Context,uri:Uri,expected:String):VerifiedUpdate=withContext(Dispatchers.IO) {
        require(validDigest(expected)){"Expected trusted SHA-256 required"}
        val dir=File(context.cacheDir,"verified_updates").apply{mkdirs()}
        val file=File.createTempFile("candidate-",".apk",dir)
        try {
            val md=MessageDigest.getInstance("SHA-256")
            val input=context.contentResolver.openInputStream(uri)?:error("Cannot open APK")
            input.use { src -> file.outputStream().use { dest ->
                val buf=ByteArray(65536);var total=0L
                while(true){val n=src.read(buf);if(n<0)break;total+=n;require(total<=200L*1024*1024){"APK too large"};md.update(buf,0,n);dest.write(buf,0,n)}
            }}
            val digest=md.digest().joinToString(""){"%02x".format(it)}
            require(digest.equals(expected,true)){"SHA-256 mismatch"}
            val pm=context.packageManager
            val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val candidate=pm.getPackageArchiveInfo(file.absolutePath,flags)?:error("Invalid APK")
            val installed=pm.getPackageInfo(context.packageName,flags)
            require(candidate.packageName==context.packageName){"Package mismatch"}
            fun certs(info:android.content.pm.PackageInfo):Set<String>{
                val signatures=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
                return signatures.orEmpty().map{MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(""){b->"%02x".format(b)}}.toSet()
            }
            val a=certs(candidate);val b=certs(installed)
            require(a.isNotEmpty() && a==b){"Signing certificate mismatch"}
            val version=if(Build.VERSION.SDK_INT>=28) candidate.longVersionCode else candidate.versionCode.toLong()
            val old=if(Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
            require(version>old){"Candidate is not a newer version"}
            VerifiedUpdate(file,digest,version)
        }catch(e:Exception){file.delete();throw e}
    }
    fun installIntent(context:Context,verified:VerifiedUpdate):Intent {
        require(verified.file.exists())
        val actual=verified.file.inputStream().use{input->
            val md=MessageDigest.getInstance("SHA-256");val buf=ByteArray(65536)
            while(true){val n=input.read(buf);if(n<0)break;md.update(buf,0,n)}
            md.digest().joinToString(""){"%02x".format(it)}
        }
        require(actual==verified.digest){"APK changed after verification"}
        val uri=FileProvider.getUriForFile(context,context.packageName+".verifiedupdates",verified.file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
