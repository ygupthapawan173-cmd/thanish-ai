package com.example

import android.app.Application
import com.example.security.CrashRecoveryHandler

class OmegaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecoveryHandler.install(this)
        com.example.data.ApiQuotaTracker.attach(com.example.data.PersistentApiTelemetry(this))
    }
}
