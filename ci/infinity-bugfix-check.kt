import com.example.ai.DirectDeviceCommand
import com.example.voice.BackgroundVoicePolicy
fun main() {
    val A = DirectDeviceCommand
    val cases = listOf(
        "Hey JARVIS, flashlight ON చెయ్" to DirectDeviceCommand.Action("toggle_flashlight", true),
        "turn off the torch" to DirectDeviceCommand.Action("toggle_flashlight", false),
        "టార్చ్ ఆన్ చేయి" to DirectDeviceCommand.Action("toggle_flashlight", true),
        "open torch" to DirectDeviceCommand.Action("toggle_flashlight", true),
        "do not turn on flashlight" to null,
        "how does flashlight work?" to null,
        "flashlight on and open whatsapp" to null,
        "what happens if volume 100" to null,
        "set volume to 42%" to DirectDeviceCommand.Action("set_media_volume",42),
        "volume 101" to null,
        "volume mute" to DirectDeviceCommand.Action("set_media_volume",0),
        "WhatsApp open chey" to DirectDeviceCommand.Action("open_app","whatsapp"),
        "open YouTube" to DirectDeviceCommand.Action("open_app","youtube"),
        "open YouTube and WhatsApp" to null,
        "how to open youtube" to null,
        "set a timer for 5 minutes" to DirectDeviceCommand.Action("set_timer",300),
        "timer 0 seconds" to null,
        "timer 99999 hours" to null,
        "battery percentage" to DirectDeviceCommand.Action("get_battery_status"),
        "battery technology" to null,
        "who created you?" to null
    )
    for ((input, expected) in cases) { check(A.parse(input)==expected) { "$input expected=$expected actual=${A.parse(input)}" }; println("PASS: $input") }
    val stops = listOf("stop everything", "emergency stop", "cancel all", "అన్నీ ఆపు")
    for (input in stops) { check(BackgroundVoicePolicy.classify(input)==BackgroundVoicePolicy.Action.STOP); println("PASS: $input") }
    check(BackgroundVoicePolicy.classify("how do I stop everything?")==BackgroundVoicePolicy.Action.QUESTION)
    println("PASS: stop question is not a stop command")
    println("${cases.size+stops.size+1}/${cases.size+stops.size+1} bugfix policy checks passed")
}
