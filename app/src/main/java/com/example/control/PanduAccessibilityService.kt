package com.example.control

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional, user-consented Accessibility Service used exclusively for global navigation
 * actions (Back, Home, Recents, Notifications) that Android restricts regular apps from executing.
 */
class PanduAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no-op: never inspects or collects screen content.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
    }

    companion object {
        @Volatile
        private var instance: PanduAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun performGlobalNav(action: GlobalNavigationAction): Boolean {
            val service = instance ?: return false
            val globalConstant = when (action) {
                GlobalNavigationAction.BACK -> GLOBAL_ACTION_BACK
                GlobalNavigationAction.HOME -> GLOBAL_ACTION_HOME
                GlobalNavigationAction.RECENTS -> GLOBAL_ACTION_RECENTS
                GlobalNavigationAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            }
            return service.performGlobalAction(globalConstant)
        }
    }
}
