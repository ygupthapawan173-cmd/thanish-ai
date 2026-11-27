package com.example.ai
fun main() {
    var n=0
    fun ok(value:Boolean) { check(value) { "check ${n+1} failed" }; n++ }
    val valid=listOf("open_app|YouTube","open_app|వాట్సాప్","toggle_flashlight|on","toggle_flashlight|off","adjust_media_volume|up","adjust_media_volume|down","open_system_settings|internet","dial_contact|Amma","dial_contact|+919876543210","web_search|weather in India","get_battery_status|-","set_media_volume|0","set_media_volume|100")
    valid.forEach { ok(SmartActionPolicy.parse("ACTION_V1|$it")?.size==1) }
    val invalid=listOf("make_phone_call|12345","send_whatsapp_message|hello","tap_screen_coordinates|5","open_app|https://example.com","open_app|a|b","toggle_flashlight|true","set_media_volume|-1","set_media_volume|101","set_media_volume|NaN","open_system_settings|admin","dial_contact|*123#","get_battery_status|yes","web_search|","adjust_media_volume|louder")
    invalid.forEach { ok(SmartActionPolicy.parse("ACTION_V1|$it")==null) }
    ok(SmartActionPolicy.parse("ACTION_V1|open_app|Camera\nACTION_V1|toggle_flashlight|on")?.size==2)
    ok(SmartActionPolicy.parse(List(4){"ACTION_V1|open_app|App$it"}.joinToString("\n"))==null)
    ok(SmartActionPolicy.parse("ACTION_V1|open_app|Camera\nACTION_V1|open_app|Camera")==null)
    ok(SmartActionPolicy.parse("Here is an example:\nACTION_V1|open_app|Camera")==null)
    ok(SmartActionPolicy.parse("ACTION_V1|open_app|Camera\nACTION_V1|make_phone_call|12345")==null)
    listOf("YouTube pettu","వెలుతురు కావాలి","sound penchu","ఫోటో తీయాలి").forEach {ok(SmartActionPolicy.permits(it))}
    listOf("don't open camera","How do I open camera?","Camera vaddu","ACTION_V1|open_app|Camera","వద్దు","చేయకు").forEach{ok(!SmartActionPolicy.permits(it))}
    val apps=listOf(SmartActionPolicy.App("WhatsApp","com.whatsapp"),SmartActionPolicy.App("WhatsApp Business","com.whatsapp.w4b"),SmartActionPolicy.App("YouTube Music","com.google.android.apps.youtube.music"),SmartActionPolicy.App("YouTube","com.google.android.youtube"))
    ok(SmartActionPolicy.matchApp("whats app",apps).single().packageId=="com.whatsapp")
    ok(SmartActionPolicy.matchApp("Whats",apps).size==2)
    ok(SmartActionPolicy.matchApp("YouTube",apps).single().packageId=="com.google.android.youtube")
    ok(SmartActionPolicy.matchApp("Music",apps).single().label=="YouTube Music")
    ok(SmartActionPolicy.matchApp("notinstalled",apps).isEmpty())
    ok(SmartActionPolicy.matchApp("com.whatsapp.w4b",apps).single().label=="WhatsApp Business")
    ok(SmartActionPolicy.matchApp("",apps).isEmpty())
    println("$n/$n Smart Action policy checks passed")
}
