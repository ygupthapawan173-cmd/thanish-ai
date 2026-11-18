package com.example.control

enum class AppControlTarget(
    val displayName: String,
    val primaryPackage: String?,
    val secondaryPackage: String? = null,
    val requiresConfirmation: Boolean = false
) {
    YOUTUBE("YouTube", "com.google.android.youtube"),
    WHATSAPP("WhatsApp", "com.whatsapp", "com.whatsapp.w4b"),
    INSTAGRAM("Instagram", "com.instagram.android"),
    CHROME("Chrome", "com.android.chrome"),
    CAMERA("Camera", null),
    GALLERY("Gallery", null),
    PHONE("Phone", null, requiresConfirmation = true),
    MESSAGES("Messages", null, requiresConfirmation = true),
    SETTINGS("Settings", null)
}

enum class DeviceSettingTarget(val displayName: String) {
    WIFI("Wi-Fi Settings"),
    BLUETOOTH("Bluetooth Settings"),
    HOTSPOT("Hotspot Settings"),
    MOBILE_NETWORK("Mobile Network")
}

enum class GlobalNavigationAction(val displayName: String) {
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recents"),
    NOTIFICATIONS("Notifications")
}

data class BatteryStatusInfo(
    val levelPercent: Int = -1,
    val isCharging: Boolean = false,
    val isFull: Boolean = false,
    val chargingSource: String = "Unknown",
    val temperatureCelsius: Float = 0f,
    val voltageMillivolts: Int = 0,
    val healthLabel: String = "Unknown"
)

data class DeviceControlState(
    val hasFlashUnit: Boolean = false,
    val isTorchOn: Boolean = false,
    val currentVolume: Int = 0,
    val maxVolume: Int = 15,
    val isMuted: Boolean = false,
    val canWriteSystemSettings: Boolean = false,
    val brightnessPercent: Int = 50,
    val battery: BatteryStatusInfo = BatteryStatusInfo(),
    val isAccessibilityEnabled: Boolean = false,
    val lastFeedbackMessage: String? = null,
    val lastFeedbackSuccess: Boolean = true
)

sealed interface PhoneControlCommand {
    data class OpenApp(val target: AppControlTarget) : PhoneControlCommand
    data class SetTorch(val enabled: Boolean) : PhoneControlCommand
    data object IncreaseVolume : PhoneControlCommand
    data object DecreaseVolume : PhoneControlCommand
    data object MuteVolume : PhoneControlCommand
    data object IncreaseBrightness : PhoneControlCommand
    data object DecreaseBrightness : PhoneControlCommand
    data object CheckBattery : PhoneControlCommand
    data class OpenDeviceSettings(val target: DeviceSettingTarget) : PhoneControlCommand
    data class PerformGlobalNav(val action: GlobalNavigationAction) : PhoneControlCommand
}

enum class MusicTransportAction { NEXT, PREVIOUS, PAUSE, PLAY, STOP }\n\nsealed interface PendingSensitiveAction {
    val title: String
    val description: String

    data class ConfirmAppLaunch(
        val target: AppControlTarget,
        override val title: String,
        override val description: String
    ) : PendingSensitiveAction

    data class ConfirmGlobalNav(
        val action: GlobalNavigationAction,
        override val title: String,
        override val description: String
    ) : PendingSensitiveAction
}

data class CommandExecutionResult(
    val succeeded: Boolean,
    val teluguResponse: String
)
