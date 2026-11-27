package com.example.ai

import com.example.control.DeviceActionExecutor
import com.example.data.AppDatabase
import org.json.JSONObject

data class LocalRouteResult(val replyText: String, val executedTool: String)

class LocalCommandRouter(
    private val executor: DeviceActionExecutor,
    private val database: AppDatabase
) {
    suspend fun tryHandleInstant(rawPrompt: String): LocalRouteResult? {
        val q = rawPrompt.trim().lowercase()
        if (q.isEmpty()) return null

        val matchedRoutine = database.getAllRoutines().firstOrNull { r ->
            val trig = r.triggerPhrase.lowercase().trim()
            trig.isNotEmpty() && (q == trig || q == "run $trig" || q == "start $trig")
        }
        if (matchedRoutine != null) {
            return LocalRouteResult(executor.executeRoutineByName(matchedRoutine.name), "routine:${matchedRoutine.name}")
        }

        return executeExplicit(rawPrompt)
    }

    /** Shared by offline and Smart API foreground routes; no model output executes here. */
    suspend fun executeExplicit(rawPrompt: String): LocalRouteResult? {
        val action = DirectDeviceCommand.parse(rawPrompt) ?: return null
        val args = JSONObject()
        when (action.tool) {
            "toggle_flashlight" -> args.put("enabled", action.argument as Boolean)
            "set_media_volume" -> args.put("percent", action.argument as Int)
            "set_timer" -> args.put("seconds", action.argument as Int)
            "open_app" -> args.put("app_name", action.argument as String)
        }
        val out = executor.executeTool(action.tool, args)
        return LocalRouteResult(out.responseJson.optString("result"), action.tool)
    }
}
