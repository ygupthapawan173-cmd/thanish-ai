package com.example.omega
import org.junit.Assert.*
import org.junit.Test
class OmegaAlwaysReadyTest {
    @Test fun startRequiresConsentVisiblePermissionsAndFreeMic() {
        fun allowed(enabled:Boolean=true,visible:Boolean=true,permissions:Boolean=true,active:Boolean=false,owner:MicOwner=MicOwner.NONE,idle:Boolean=true,blocked:Boolean=false,handoff:Boolean=false)=
            OmegaAlwaysReady.canStart(enabled,visible,permissions,active,owner,idle,blocked,handoff)
        assertTrue(allowed())
        assertFalse(allowed(enabled=false))
        assertFalse(allowed(visible=false))
        assertFalse(allowed(permissions=false))
        assertFalse(allowed(active=true))
        assertFalse(allowed(owner=MicOwner.LIVE))
        assertFalse(allowed(owner=MicOwner.MANUAL))
        assertFalse(allowed(owner=MicOwner.WAKE))
        assertFalse(allowed(idle=false))
        assertFalse(allowed(blocked=true))
        assertFalse(allowed(handoff=true))
    }
}
