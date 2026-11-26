package com.example.ai

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import com.example.omega.OmegaLiveRoute

/** Only active during an explicitly consented Live session. Never pairs devices or scans nearby devices. */
class OmegaHeadsetRouter(private val manager:AudioManager,private val handler:Handler,
    private val preference:OmegaLiveRoute,private val status:(String)->Unit) {
    private var running=false
    private var selectedId:Int?=null
    private var restoreDevice=false
    private var retries=0
    private var callbackRegistered=false
    private val applyLater=Runnable{applyRoute()}
    private val callback=object:AudioDeviceCallback(){
        override fun onAudioDevicesAdded(addedDevices:Array<out AudioDeviceInfo>){changed()}
        override fun onAudioDevicesRemoved(removedDevices:Array<out AudioDeviceInfo>){changed()}
    }
    private fun changed(){
        if(!running)return
        retries=0
        handler.removeCallbacks(applyLater)
        handler.postDelayed(applyLater,250)
    }
    fun start(){
        if(running)return
        running=true
        runCatching{manager.registerAudioDeviceCallback(callback,handler);callbackRegistered=true}
        applyRoute()
    }
    private fun label(type:Int)=when(type){
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO->"Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET->"Bluetooth LE headset"
        AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES->"Wired headset"
        AudioDeviceInfo.TYPE_USB_HEADSET->"USB headset"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER->"Phone speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"Earpiece"
        else->"System audio device"
    }
    private fun applyRoute(){
        if(!running)return
        if(Build.VERSION.SDK_INT<31){status("Legacy Android: system-managed headset route");return}
        try{
            val devices=manager.availableCommunicationDevices
            val target=when(preference){
                OmegaLiveRoute.SYSTEM->null
                OmegaLiveRoute.SPEAKER->devices.firstOrNull{it.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}
                OmegaLiveRoute.HEADSET->devices.firstOrNull{it.type==AudioDeviceInfo.TYPE_BLE_HEADSET}
                    ?:devices.firstOrNull{it.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO}
                    ?:devices.firstOrNull{it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_USB_HEADSET)}
            }
            if(target==null){
                if(restoreDevice){manager.clearCommunicationDevice();restoreDevice=false;selectedId=null}
                status((if(preference==OmegaLiveRoute.HEADSET)"Headset unavailable · system fallback: " else "")+(manager.communicationDevice?.let{label(it.type)}?:"System"))
                return
            }
            if(manager.communicationDevice?.id==target.id){
                status(label(target.type));return
            }
            if(selectedId!=target.id){selectedId=target.id;retries=0}
            if(retries>=2){
                if(restoreDevice){manager.clearCommunicationDevice();restoreDevice=false}
                status("Headset/route not confirmed · system fallback")
                return
            }
            retries++
            if(manager.setCommunicationDevice(target)){restoreDevice=true;status("Requesting "+label(target.type))}
            else status("Route request failed · retry "+retries+"/2")
            handler.removeCallbacks(applyLater);handler.postDelayed(applyLater,1200)
        }catch(_:Exception){status("Audio route unavailable · system manages audio")}
    }
    fun stop(){
        running=false
        handler.removeCallbacks(applyLater)
        if(callbackRegistered)runCatching{manager.unregisterAudioDeviceCallback(callback)}
        callbackRegistered=false
        if(Build.VERSION.SDK_INT>=31 && restoreDevice)runCatching{manager.clearCommunicationDevice()}
        restoreDevice=false;selectedId=null;retries=0
    }
}
