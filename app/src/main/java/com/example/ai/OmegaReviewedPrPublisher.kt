package com.example.ai
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One explicitly reviewed Kotlin replacement, on a new branch, with a draft PR. */
object OmegaReviewedPrPublisher {
    const val REPO="ygupthapawan173-cmd/thanish-ai"
    const val BASE="review/omega-10000x-singularity-v2"
    private const val API="https://api.github.com/repos/"+REPO
    fun validReplacement(path:String,old:String,next:String):Boolean =
        SelfEvolutionCompilerLoop.validPath(path) && old.isNotBlank() && old.length<=64000 &&
        next.length in 1..64000 && old!=next && next.lineSequence().any{it.trim().startsWith("package com.example")}
    suspend fun publish(token:String,path:String,old:String,replacement:String):String=withContext(Dispatchers.IO) {
        require(token.isNotBlank() && token.length<=512)
        require(validReplacement(path,old,replacement)){"Provide reviewed complete Kotlin source"}
        val client=OkHttpClient.Builder().followRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
        fun request(method:String,url:String,payload:JSONObject?=null):JSONObject{
            val builder=Request.Builder().url(url).header("Authorization","Bearer "+token)
                .header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28")
            if(payload!=null)builder.method(method,payload.toString().toRequestBody("application/json".toMediaType())) else builder.get()
            client.newCall(builder.build()).execute().use{response->
                check(response.isSuccessful){"GitHub HTTP "+response.code}
                return JSONObject(response.body?.string()?:error("Empty GitHub response"))
            }
        }
        val head=request("GET",API+"/git/ref/heads/"+BASE).getJSONObject("object").getString("sha")
        val file=request("GET",API+"/contents/"+path+"?ref="+head)
        val current=String(Base64.decode(file.getString("content"),Base64.DEFAULT),Charsets.UTF_8)
        require(current==old){"Base source changed or pasted source differs; reload before publishing"}
        val branch="review/evolution-"+UUID.randomUUID().toString().take(12)
        request("POST",API+"/git/refs",JSONObject().put("ref","refs/heads/"+branch).put("sha",head))
        try {
            request("PUT",API+"/contents/"+path,JSONObject().put("message","Reviewed assistant proposal: "+path.substringAfterLast('/'))
                .put("content",Base64.encodeToString(replacement.toByteArray(Charsets.UTF_8),Base64.NO_WRAP))
                .put("sha",file.getString("sha")).put("branch",branch))
            request("POST",API+"/pulls",JSONObject().put("title","Reviewed assistant proposal: "+path.substringAfterLast('/'))
                .put("head",branch).put("base",BASE).put("draft",true)
                .put("body","User-reviewed replacement. No automatic merge or install. Review and run CI before accepting.")).getString("html_url")
        }catch(e:Exception){error("Draft not completed. Inspect branch "+branch+" in GitHub; no merge performed.")}
    }
}
