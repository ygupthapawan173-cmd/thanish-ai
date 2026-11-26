package com.example.omega

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remember explicit consent. Only visible UI requests a new microphone foreground service. */
object OmegaAlwaysReady {
    private fun prefs(c:Context)=c.getSharedPreferences("omega_voice",Context.MODE_PRIVATE)
    private val _enabled=MutableStateFlow(false)
    val enabled=_enabled.asStateFlow()
    private val _handoff=MutableStateFlow(false)
    val handoff=_handoff.asStateFlow()
    private val _retryBlocked=MutableStateFlow(false)
    val retryBlocked=_retryBlocked.asStateFlow()
    private var startPending=false
    fun refresh(c:Context, allowRetry:Boolean=false) {
        _enabled.value=prefs(c).getBoolean("always_ready_opt_in",false)
        if(allowRetry && !OmegaWakeService.active.value) _retryBlocked.value=false
    }
    fun setEnabled(c:Context,value:Boolean) {
        prefs(c).edit().putBoolean("always_ready_opt_in",value).apply()
        _enabled.value=value
        _retryBlocked.value=false
        if(!value){startPending=false;OmegaWakeService.stop(c)}
    }
    fun setHandoff(value:Boolean){_handoff.value=value}
    fun onServiceStarted(){startPending=false}
    fun onServiceStopped(){startPending=false}
    fun onFailure(){startPending=false;_retryBlocked.value=true}
    fun missingPermissions(c:Context):List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter{ContextCompat.checkSelfPermission(c,it)!=PackageManager.PERMISSION_GRANTED}
    fun canStart(enabled:Boolean,visible:Boolean,permissions:Boolean,active:Boolean,owner:MicOwner,idle:Boolean,blocked:Boolean,handoff:Boolean):Boolean =
        enabled && visible && permissions && !active && owner==MicOwner.NONE && idle && !blocked && !handoff
    /** Must run on main thread from a resumed activity; no boot or worker startup. */
    fun tryStart(c:Context,visible:Boolean,idle:Boolean=true):Boolean {
        if(startPending || !canStart(_enabled.value,visible,missingPermissions(c).isEmpty(),
                OmegaWakeService.active.value,OmegaAssistantCore.sessions.owner.value,idle,_retryBlocked.value,_handoff.value)) return false
        startPending=true
        return try { OmegaWakeService.start(c);true }
        catch(_:Exception){onFailure();OmegaWakeService.lastError.value="Always Ready could not start. Reopen app or toggle Always Ready.";false}
    }
}
