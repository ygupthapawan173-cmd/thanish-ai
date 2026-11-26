package com.example.control

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deterministic reviewed chain. Unsupported steps fail before execution; no screen scraping. */
class AutonomousReActOrchestrator(private val manager:PhoneControlManager) {
    data class Trace(val steps:List<String> = emptyList(),val index:Int=0,val status:String="Idle",val logs:List<String> = emptyList())
    private val _trace=MutableStateFlow(Trace())
    private var revision=0L
    val trace=_trace.asStateFlow()
    fun prepare(input:String) {
        revision++
        val steps=parseChain(input)
        _trace.value=if(steps==null) Trace(status="Unsupported or invalid chain. Use supported phone commands.")
            else Trace(steps=steps,status="Awaiting approval for step 1")
    }
    fun cancel(){revision++;_trace.value=_trace.value.copy(status="Cancelled",index=_trace.value.steps.size)}
    suspend fun approveNext() {
        val t=_trace.value
        val text=t.steps.getOrNull(t.index)?:return
        if(!t.status.startsWith("Awaiting")) return
        val cmd=PhoneCommandParser.parse(text)?:return
        val run=revision
        manager.refreshState()
        val before=manager.state.value
        _trace.value=t.copy(status="Executing approved step")
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
        if(run!=revision)return
        var proof:Boolean?=null
        if(result.succeeded) for(attempt in 0..2) {
            delay(250)
            if(run!=revision)return
            manager.refreshState()
            proof=verifyState(cmd,before,manager.state.value)
            if(proof!=false)break
        }
        val accepted=result.succeeded && proof!=false
        val next=t.index+1
        val evidence=if(!result.succeeded) "OS failed" else when(proof){true->"State verified";false->"State not verified";null->"OS accepted; outcome not observable"}
        _trace.value=t.copy(index=if(accepted)next else t.steps.size,
            status=if(!accepted) "Failed verification — chain stopped; review a revised chain" else if(next==t.steps.size) "Completed: reviewed actions processed" else "Awaiting approval for step "+(next+1),
            logs=t.logs+(evidence+": "+text+" · "+result.teluguResponse))
    }
    companion object {
        fun verifyState(cmd:PhoneControlCommand,before:DeviceControlState,after:DeviceControlState):Boolean? = when(cmd) {
            is PhoneControlCommand.SetTorch -> after.isTorchOn==cmd.enabled
            PhoneControlCommand.IncreaseVolume -> after.currentVolume>=minOf(before.currentVolume+1,before.maxVolume)
            PhoneControlCommand.DecreaseVolume -> after.currentVolume<=maxOf(before.currentVolume-1,0)
            PhoneControlCommand.MuteVolume -> after.currentVolume==0
            PhoneControlCommand.IncreaseBrightness -> after.brightnessPercent>=minOf(before.brightnessPercent+13,100)
            PhoneControlCommand.DecreaseBrightness -> after.brightnessPercent<=maxOf(before.brightnessPercent-13,0)
            PhoneControlCommand.CheckBattery -> after.battery.levelPercent in 0..100
            else -> null
        }
        fun parseChain(input:String):List<String>? {
            if(input.length>1500) return null
            val steps=input.split("->").map{it.trim()}
            if(steps.size !in 1..8 || steps.any{it.isBlank() || PhoneCommandParser.parse(it)==null}) return null
            return steps
        }
    }
}
