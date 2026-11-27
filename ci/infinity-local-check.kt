import com.example.ai.CreatorIdentity
import com.example.data.OrbColorPreset
import com.example.data.RenderQuality
fun main() {
 val checks = listOf(
 "11 palettes" to (OrbColorPreset.entries.size == 11),
 "11 unique labels" to (OrbColorPreset.entries.map { it.label }.toSet().size == 11),
 "11 unique background colors" to (OrbColorPreset.entries.map { it.backgroundHex }.toSet().size == 11),
 "default" to (OrbColorPreset.fromStored(null)==OrbColorPreset.AURORA_CYAN),
 "unknown fallback" to (OrbColorPreset.fromStored("unknown")==OrbColorPreset.AURORA_CYAN),
 "legacy solar" to (OrbColorPreset.fromStored("SOLAR_NOVA")==OrbColorPreset.SOLAR_NOVA),
 "legacy cyan" to (OrbColorPreset.fromStored("AURORA_CYAN")==OrbColorPreset.AURORA_CYAN),
 "wake creator English" to (CreatorIdentity.reply("Hey JARVIS, who created you?")?.contains("Yeluchuri Guptha")==true),
 "creator English" to (CreatorIdentity.reply("WHO MADE YOU?")?.contains("Yeluchuri Guptha")==true),
 "Telugu" to (CreatorIdentity.reply("నిన్ను ఎవరు సృష్టించారు?")?.contains("యెలుచూరి గుప్తా")==true),
 "Tenglish" to (CreatorIdentity.reply("ninnu evaru create chesaru")!=null),
 "model provider question not intercepted" to (CreatorIdentity.reply("who created Gemini?")==null),
 "device action not intercepted" to (CreatorIdentity.reply("open flashlight")==null),
 "compound identity not intercepted" to (CreatorIdentity.reply("who created you and who created Gemini?")==null),
 "creator app phrasing" to (CreatorIdentity.reply("Who developed this app?")!=null),
 "creator mixed casual" to (CreatorIdentity.reply("Hey Jarvis, ninnu evaru create chesav?")!=null),
 "identity remains bounded" to (CreatorIdentity.reply("who made this app and send message") == null),
 "adaptive normal" to !RenderQuality.ADAPTIVE.reduced(false,false),
 "adaptive low" to RenderQuality.ADAPTIVE.reduced(false,true),
 "high ignores low battery" to !RenderQuality.HIGH.reduced(false,true),
 "system saver overrides high" to RenderQuality.HIGH.reduced(true,false),
 "saver always reduced" to RenderQuality.SAVER.reduced(false,false),
 "legacy render fallback" to (RenderQuality.fromStored("invalid")==RenderQuality.ADAPTIVE),
 "saved mode" to (RenderQuality.fromStored("SAVER")==RenderQuality.SAVER))
 checks.forEach { (label,pass) -> println("${if(pass) "PASS" else "FAIL"}: $label") }
 println("${checks.count { it.second }}/${checks.size} passed")
 check(checks.all { it.second })
}
