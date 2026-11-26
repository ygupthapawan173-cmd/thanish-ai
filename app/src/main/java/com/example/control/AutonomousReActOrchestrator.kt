package com.example.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deterministic reviewed chain. Unsupported steps fail before execution; no screen scraping. */
class AutonomousReActOrchestrator(private val manager:PhoneControlManager) {
    data class Trace(val steps:List<String> = emptyList(),val index:Int=0,val status:String="Idle",val logs:List<String> = emptyList())
    private val _trace=MutableStateFlow(Trace())
    val trace=_trace.asStateFlow()
    fun prepare(input:String) {
        val steps=parseChain(input)
        _trace.value=if(steps==null) Trace(status="Unsupported or invalid chain. Use supported phone commands.")
            else Trace(steps=steps,status="Awaiting approval for step 1")
    }
    fun cancel(){_trace.value=_trace.value.copy(status="Cancelled",index=_trace.value.steps.size)}
    fun approveNext() {
        val t=_trace.value
        val text=t.steps.getOrNull(t.index)?:return
        if(!t.status.startsWith("Awaiting")) return
        val cmd=PhoneCommandParser.parse(text)?:return
        val result=try { when(cmd) {
            is PhoneControlCommand.OpenApp -> manager.openApp(cmd.target)
            is PhoneControlCommand.SetTorch -> manager.setTorchEnabled(cmd.enabled)
            PhoneControlCommand.IncreaseVolume -> manager.adjustVolume(1)
            PhoneControlCommand.DecreaseVolume -> manager.adjustVolume(-1)
            PhoneControlCommand.MuteVolume -> manager.setVolumeLevel(0)
            PhoneControlCommand.IncreaseBrightness -> manager.adjustBrightness(15)
            PhoneControlCommand.DecreaseBrightness -> manager.adjustBrightness(-15)
            PhoneControlCommand.CheckBattery -> manager.readBatterySummary()
            is PhoneControlCommand.OpenDeviceSettings -> manager.openDeviceSetting(cmd.target)
            is PhoneControlCommand.PerformGlobalNav -> manager.performGlobalNavigation(cmd.action)
        }} catch(_:Exception){CommandExecutionResult(false,"Device action failed")}
        val next=t.index+1
        _trace.value=t.copy(index=if(result.succeeded) next else t.steps.size,
            status=if(!result.succeeded) "Failed — chain stopped" else if(next==t.steps.size) "Completed: OS accepted all steps" else "Awaiting approval for step "+(next+1),
            logs=t.logs+((if(result.succeeded) "Accepted: " else "Failed: ")+text+" · "+result.teluguResponse))
    }
    companion object {
        fun parseChain(input:String):List<String>? {
            if(input.length>1500) return null
            val steps=input.split("->").map{it.trim()}
            if(steps.size !in 1..8 || steps.any{it.isBlank() || PhoneCommandParser.parse(it)==null}) return null
            return steps
        }
    }
}
