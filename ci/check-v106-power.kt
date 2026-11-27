package com.example.voice
import com.example.ai.SmartModelPolicy
fun main() {
 var count=0
 fun checkIt(b:Boolean) { check(b); count++ }
 val p=CloudVoicePolicy
 for (en in p.english) for(hd in listOf(false,true)) {
  checkIt(p.select("Hello",en,"anushka",hd).voice==en)
  checkIt(p.select("Hello",en,"anushka",hd).model==if(hd) "openai/tts-1-hd" else "openai/tts-1")
 }
 for(te in p.telugu) { checkIt(p.select("తెలుగు", "onyx",te,false).voice==te); checkIt(p.select("తెలుగు", "onyx",te,true).model=="sarvam/bulbul-v2") }
 checkIt(p.select("తెలుగు Hello","bad","bad",false).voice=="anushka")
 checkIt(p.select("Hello","bad","bad",false).voice=="onyx")
 for (enabled in listOf(false,true)) for(gateway in listOf(false,true)) for(free in listOf(false,true)) for(privateMode in listOf(false,true)) for(foreground in listOf(false,true)) for(key in listOf(false,true)) for(len in listOf(0,1,1000,1001)) {
  checkIt(p.allowed(p.BASE,enabled,gateway,free,0.0,privateMode,foreground,key,len)==(enabled && gateway && !free && !privateMode && foreground && key && len in 1..1000))
 }
 for(base in listOf("https://evil.example/v1", "http://api.aicredits.in/v1", p.BASE+"/chat/completions")) checkIt(!p.allowed(base,true,true,false,0.0,false,true,true,10))
 checkIt(!p.allowed(p.BASE,true,true,false,1.0,false,true,true,10))
 checkIt(p.allowed(p.BASE+"/",true,true,false,0.0,false,true,true,10))
 for(type in listOf("audio/mpeg","audio/wav; charset=binary","application/octet-stream")) checkIt(p.audioType(type))
 for(type in listOf(null,"application/json","text/html")) checkIt(!p.audioType(type))
 for(id in listOf("sarvam/bulbul-v2","sarvam/saarika-v2","sarvam/saaras-v3","mistralai/voxtral-small","openai/tts-1")) checkIt(!SmartModelPolicy.eligible(SmartModelPolicy.Model(id),SmartModelPolicy.Task.CHAT))
 checkIt(SmartModelPolicy.eligible(SmartModelPolicy.Model("google/gemini-2.5-flash"),SmartModelPolicy.Task.CHAT))
 println("$count/$count V101 standalone Kotlin policy assertions passed")
}
