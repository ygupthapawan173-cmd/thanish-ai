package com.example.ai

/** Only explicit, bounded utterances may cause a local device action. */
internal object DirectDeviceCommand {
    data class Action(val tool: String, val argument: Any? = null)
    fun parse(raw: String): Action? {
        val q = raw.lowercase().trim().trimEnd('.', '!').trim()
            .replace(Regex("\\s+"), " ").replace(Regex("^hey jarvis[, ]+"), "")
        val core = q.removePrefix("please ").removeSuffix(" please").trim()
        val torch = "(?:flashlight|torch|టార్చ్|ఫ్లాష్ లైట్)"
        val on = "(?:on|ఆన్)"; val off = "(?:off|ఆఫ్|bandh)"
        val suffix = "(?: (?:chey|cheyi|cheyyi|cheyu|chesey|veyi|చేయి|చెయ్|వేయి))?"
        if (Regex("^(?:open|enable) $torch$suffix$").matches(core)) return Action("toggle_flashlight", true)
        if (Regex("^(?:close|disable) $torch$suffix$").matches(core)) return Action("toggle_flashlight", false)
        if (Regex("^(?:turn |switch )?$on (?:the )?$torch$suffix$").matches(core) || Regex("^$torch $on$suffix$").matches(core)) return Action("toggle_flashlight", true)
        if (Regex("^(?:turn |switch )?$off (?:the )?$torch$suffix$").matches(core) || Regex("^$torch $off$suffix$").matches(core)) return Action("toggle_flashlight", false)
        val volume = Regex("^(?:set |change )?(?:media )?(?:volume|sound|వాల్యూమ్) (?:to )?(\\d{1,3})\\s*(?:%|percent)?$suffix$").matchEntire(core)
        if (volume != null) return volume.groupValues[1].toIntOrNull()?.takeIf { it in 0..100 }?.let { Action("set_media_volume", it) }
        if (core in setOf("mute", "mute volume", "volume mute", "volume zero", "volume silent")) return Action("set_media_volume", 0)
        if (core in setOf("volume max", "volume full", "maximum volume")) return Action("set_media_volume", 100)
        if (core in setOf("volume half", "volume medium")) return Action("set_media_volume", 50)
        if (core.trimEnd('?') in setOf("battery", "battery level", "battery percentage", "how much battery is left", "what is my battery percentage", "బ్యాటరీ", "బ్యాటరీ ఎంత", "battery entha")) return Action("get_battery_status")
        val timer = Regex("^(?:set |start )?(?:a )?timer (?:for )?(\\d{1,5}) (seconds?|minutes?|hours?)$suffix$").matchEntire(core)
        if (timer != null) {
            val unit = timer.groupValues[2]; val factor = if (unit.startsWith("hour")) 3600L else if (unit.startsWith("minute")) 60L else 1L
            val seconds = timer.groupValues[1].toLong() * factor
            if (seconds in 1..86400) return Action("set_timer", seconds.toInt())
            return null
        }
        val app = Regex("^(?:open|launch) (?:the )?([a-z0-9][a-z0-9 ._-]{0,39})$").matchEntire(core)?.groupValues?.get(1)
            ?: Regex("^([a-z0-9][a-z0-9 ._-]{0,39}) open(?: chey| cheyi| చెయ్| చేయి)?$").matchEntire(core)?.groupValues?.get(1)
        if (app != null && !Regex("\\b(and|then|or|not|chesi|after|before|if|to|what|how)\\b").containsMatchIn(app)) return Action("open_app", app.trim())
        return null
    }
}
