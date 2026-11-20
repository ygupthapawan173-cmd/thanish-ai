package com.example.control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "PhoneControlManager"

class PhoneControlManager(
    private val context: Context,
    private val canWriteSettingsProvider: () -> Boolean = {
        Settings.System.canWrite(context.applicationContext)
    }
) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var torchCameraId: String? = null

    private val _state = MutableStateFlow(DeviceControlState())
    val state: StateFlow<DeviceControlState> = _state.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _state.update { it.copy(isTorchOn = enabled) }
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                _state.update { it.copy(isTorchOn = false) }
            }
        }
    }

    private val batteryMonitor = BatteryMonitorHelper(appContext)
    private val batteryListener: (BatteryStatusInfo) -> Unit = { info ->
        _state.update { it.copy(battery = info) }
    }

    init {
        discoverTorchCamera()
        runCatching {
            cameraManager.registerTorchCallback(torchCallback, mainHandler)
        }.onFailure { Log.w(TAG, "Unable to register TorchCallback", it) }

        batteryMonitor.addListener(batteryListener)
        batteryMonitor.startMonitoring()

        refreshState()
    }

    private fun discoverTorchCamera() {
        try {
            val ids = cameraManager.cameraIdList
            for (id in ids) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id
                    break
                }
            }
            if (torchCameraId == null) {
                torchCameraId = ids.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }
            _state.update { it.copy(hasFlashUnit = torchCameraId != null) }
        } catch (e: Exception) {
            Log.w(TAG, "Error discovering camera flash unit", e)
            torchCameraId = null
            _state.update { it.copy(hasFlashUnit = false) }
        }
    }

    fun refreshState() {
        val currentVol = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val maxVol = runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1)
        val canWrite = canWriteSettingsProvider()
        val brightnessPercent = readSystemBrightnessPercent()
        val isMuted = currentVol == 0
        val batteryInfo = batteryMonitor.refresh()

        _state.update {
            it.copy(
                hasFlashUnit = torchCameraId != null,
                currentVolume = currentVol,
                maxVolume = maxVol,
                isMuted = isMuted,
                canWriteSystemSettings = canWrite,
                brightnessPercent = brightnessPercent,
                battery = batteryInfo,
                isAccessibilityEnabled = isPanduAccessibilityEnabled()
            )
        }
    }

    private fun isPanduAccessibilityEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return runCatching {
            val expected = ComponentName(appContext, PanduAccessibilityService::class.java)
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo.serviceInfo
                    ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
                }
        }.getOrDefault(false)
    }

    private fun readSystemBrightnessPercent(): Int {
        return try {
            val raw = Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            ).coerceIn(0, 255)
            ((raw / 255f) * 100f).roundToInt().coerceIn(5, 100)
        } catch (e: Exception) {
            50
        }
    }

    fun openApp(target: AppControlTarget): CommandExecutionResult {
        val pm = appContext.packageManager
        val result: CommandExecutionResult = when (target) {
            AppControlTarget.YOUTUBE,
            AppControlTarget.WHATSAPP,
            AppControlTarget.INSTAGRAM -> {
                val pkgIntent = target.primaryPackage?.let { pm.getLaunchIntentForPackage(it) }
                    ?: target.secondaryPackage?.let { pm.getLaunchIntentForPackage(it) }
                if (pkgIntent != null) {
                    launchActivityIntent(pkgIntent, target.displayName)
                } else {
                    CommandExecutionResult(
                        succeeded = false,
                        teluguResponse = "అయ్యో బాస్! ఈ ఫోన్‌లో ${target.displayName} యాప్ ఇన్‌స్టాల్ అయి లేదు. అందుకే ఓపెన్ చేయలేకపోయాను."
                    )
                }
            }

            AppControlTarget.CHROME -> {
                val chromeIntent = target.primaryPackage?.let { pm.getLaunchIntentForPackage(it) }
                if (chromeIntent != null) {
                    launchActivityIntent(chromeIntent, "Chrome")
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                    if (browserIntent.resolveActivity(pm) != null) {
                        val launched = launchActivityIntent(browserIntent, "Browser")
                        if (launched.succeeded) {
                            CommandExecutionResult(
                                succeeded = true,
                                teluguResponse = "Chrome యాప్ ప్రత్యేకంగా లేదు బాస్, కానీ డిఫాల్ట్ బ్రౌజర్ ఓపెన్ చేశాను!"
                            )
                        } else launched
                    } else {
                        CommandExecutionResult(
                            succeeded = false,
                            teluguResponse = "బాస్, ఈ డివైజ్‌లో Chrome లేదా వెబ్ బ్రౌజర్ అందుబాటులో లేదు."
                        )
                    }
                }
            }

            AppControlTarget.CAMERA -> {
                val stillCam = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                val captureFallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                when {
                    stillCam.resolveActivity(pm) != null -> launchActivityIntent(stillCam, "Camera")
                    captureFallback.resolveActivity(pm) != null -> launchActivityIntent(captureFallback, "Camera")
                    else -> CommandExecutionResult(
                        succeeded = false,
                        teluguResponse = "బాస్, ఈ డివైజ్‌లో కెమెరా యాప్ దొరకలేదు."
                    )
                }
            }

            AppControlTarget.GALLERY -> {
                val galleryIntent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_GALLERY
                )
                val imageViewFallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                }
                when {
                    galleryIntent.resolveActivity(pm) != null -> launchActivityIntent(galleryIntent, "Gallery")
                    imageViewFallback.resolveActivity(pm) != null -> launchActivityIntent(imageViewFallback, "Gallery")
                    else -> CommandExecutionResult(
                        succeeded = false,
                        teluguResponse = "అయ్యో బాస్, ఈ డివైజ్‌లో గ్యాలరీ యాప్ దొరకలేదు."
                    )
                }
            }

            AppControlTarget.PHONE -> {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                if (dialIntent.resolveActivity(pm) != null) {
                    launchActivityIntent(dialIntent, "Phone")
                } else {
                    CommandExecutionResult(
                        succeeded = false,
                        teluguResponse = "బాస్, ఈ డివైజ్‌లో ఫోన్ డయలర్ అందుబాటులో లేదు."
                    )
                }
            }

            AppControlTarget.MESSAGES -> {
                val msgIntent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MESSAGING
                )
                val smsFallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))
                when {
                    msgIntent.resolveActivity(pm) != null -> launchActivityIntent(msgIntent, "Messages")
                    smsFallback.resolveActivity(pm) != null -> launchActivityIntent(smsFallback, "Messages")
                    else -> CommandExecutionResult(
                        succeeded = false,
                        teluguResponse = "బాస్, ఈ డివైజ్‌లో మెసేజెస్ యాప్ అందుబాటులో లేదు."
                    )
                }
            }

            AppControlTarget.SETTINGS -> {
                launchActivityIntent(Intent(Settings.ACTION_SETTINGS), "Settings")
            }
        }

        recordFeedback(result)
        return result
    }

    private fun launchActivityIntent(intent: Intent, label: String): CommandExecutionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            CommandExecutionResult(
                succeeded = true,
                teluguResponse = "డన్ బాస్! $label ఓపెన్ చేశాను!"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching $label", e)
            CommandExecutionResult(
                succeeded = false,
                teluguResponse = "అయ్యో బాస్, $label ఓపెన్ చేయడంలో సమస్య వచ్చింది: ${e.localizedMessage ?: "Unsupported"}"
            )
        }
    }

    fun setTorchEnabled(enabled: Boolean): CommandExecutionResult {
        val camId = torchCameraId
        if (camId == null) {
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "అయ్యో బాస్! ఈ డివైజ్‌లో ఫ్లాష్‌లైట్ (టార్చ్) హార్డ్‌వేర్ లేదు, అందుకే ఆన్ చేయలేకపోయాను."
            )
            recordFeedback(res)
            return res
        }

        return try {
            cameraManager.setTorchMode(camId, enabled)
            _state.update { it.copy(isTorchOn = enabled) }
            val res = CommandExecutionResult(
                succeeded = true,
                teluguResponse = if (enabled) {
                    "టార్చ్ ఆన్ చేశాను బాస్! మొత్తం వెలుగులే వెలుగులు!"
                } else {
                    "టార్చ్ ఆఫ్ చేశాను బాస్!"
                }
            )
            recordFeedback(res)
            res
        } catch (e: Exception) {
            Log.e(TAG, "setTorchMode failed", e)
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "అయ్యో బాస్, కెమెరా ఫ్లాష్ బిజీగా ఉంది లేదా అందుబాటులో లేదు. టార్చ్ మార్చలేకపోయాను."
            )
            recordFeedback(res)
            res
        }
    }

    fun adjustVolume(deltaSteps: Int): CommandExecutionResult {
        return try {
            val direction = if (deltaSteps >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            refreshState()
            val updated = _state.value
            val res = CommandExecutionResult(
                succeeded = true,
                teluguResponse = if (deltaSteps >= 0) {
                    "వాల్యూమ్ పెంచాను బాస్! ఇప్పుడు ${updated.currentVolume}/${updated.maxVolume} లో ఉంది."
                } else {
                    "వాల్యూమ్ తగ్గించాను బాస్! ఇప్పుడు ${updated.currentVolume}/${updated.maxVolume} లో ఉంది."
                }
            )
            recordFeedback(res)
            res
        } catch (e: Exception) {
            Log.e(TAG, "adjustVolume failed", e)
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "వాల్యూమ్ మార్చలేకపోయాను బాస్: ${e.localizedMessage ?: "Audio error"}"
            )
            recordFeedback(res)
            res
        }
    }

    fun setVolumeLevel(level: Int): CommandExecutionResult {
        return try {
            val max = _state.value.maxVolume.coerceAtLeast(1)
            val clamped = level.coerceIn(0, max)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                clamped,
                AudioManager.FLAG_SHOW_UI
            )
            refreshState()
            val res = CommandExecutionResult(
                succeeded = true,
                teluguResponse = if (clamped == 0) {
                    "మీడియా వాల్యూమ్ మ్యూట్ చేశాను బాస్!"
                } else {
                    "వాల్యూమ్ $clamped/$max కి సెట్ చేశాను బాస్!"
                }
            )
            recordFeedback(res)
            res
        } catch (e: Exception) {
            Log.e(TAG, "setVolumeLevel failed", e)
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "వాల్యూమ్ సెట్ చేయడంలో సమస్య వచ్చింది బాస్."
            )
            recordFeedback(res)
            res
        }
    }

    fun setSystemBrightnessPercent(percent: Int): CommandExecutionResult {
        val clamped = percent.coerceIn(5, 100)
        if (!canWriteSettingsProvider()) {
            refreshState()
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "బాస్, Android సెక్యూరిటీ ప్రకారం స్క్రీన్ బ్రైట్‌నెస్ మార్చాలంటే 'Modify System Settings' పర్మిషన్ ఇవ్వాలి. దయచేసి పర్మిషన్ ఆన్ చేయండి!"
            )
            recordFeedback(res)
            return res
        }

        return try {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val rawValue = ((clamped / 100f) * 255f).roundToInt().coerceIn(10, 255)
            val written = Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                rawValue
            )
            refreshState()
            val res = if (written) {
                CommandExecutionResult(
                    succeeded = true,
                    teluguResponse = "స్క్రీన్ బ్రైట్‌నెస్ $clamped% కి సెట్ చేశాను బాస్!"
                )
            } else {
                CommandExecutionResult(
                    succeeded = false,
                    teluguResponse = "సిస్టమ్ బ్రైట్‌నెస్ రాయడం సాధ్యం కాలేదు బాస్."
                )
            }
            recordFeedback(res)
            res
        } catch (e: Exception) {
            Log.e(TAG, "setSystemBrightnessPercent failed", e)
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "బ్రైట్‌నెస్ మార్చలేకపోయాను బాస్: ${e.localizedMessage ?: "Permission required"}"
            )
            recordFeedback(res)
            res
        }
    }

    fun adjustBrightness(deltaPercent: Int): CommandExecutionResult {
        val current = readSystemBrightnessPercent()
        return setSystemBrightnessPercent(current + deltaPercent)
    }

    fun createWriteSettingsPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun readBatterySummary(): CommandExecutionResult {
        refreshState()
        val b = _state.value.battery
        if (b.levelPercent < 0) {
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "బ్యాటరీ సమాచారం చదవలేకపోయాను బాస్."
            )
            recordFeedback(res)
            return res
        }
        val chargingText = if (b.isCharging) {
            "ప్రస్తుతం ${b.chargingSource} ద్వారా ఛార్జింగ్ అవుతోంది"
        } else {
            "ఛార్జర్ కనెక్ట్ కాలేదు"
        }
        val tempText = String.format(Locale.US, "%.1f", b.temperatureCelsius)
        val res = CommandExecutionResult(
            succeeded = true,
            teluguResponse = "బాస్, బ్యాటరీ ${b.levelPercent}% ఉంది. $chargingText. టెంపరేచర్ ${tempText}°C, హెల్త్ ${b.healthLabel} గా ఉంది!"
        )
        recordFeedback(res)
        return res
    }

    fun openDeviceSetting(target: DeviceSettingTarget): CommandExecutionResult {
        val pm = appContext.packageManager
        val candidates: List<Intent> = when (target) {
            DeviceSettingTarget.WIFI -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    listOf(
                        Intent(Settings.Panel.ACTION_WIFI),
                        Intent(Settings.ACTION_WIFI_SETTINGS)
                    )
                } else {
                    listOf(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }

            DeviceSettingTarget.BLUETOOTH -> listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

            DeviceSettingTarget.HOTSPOT -> {
                val tetherIntent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName("com.android.settings", "com.android.settings.TetherSettings")
                }
                val tetherActivity = Intent(Intent.ACTION_MAIN).apply {
                    setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")
                }
                listOf(
                    tetherActivity,
                    tetherIntent,
                    Intent(Settings.ACTION_WIRELESS_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
            }

            DeviceSettingTarget.MOBILE_NETWORK -> listOf(
                Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
        }

        var launched = false
        for (candidate in candidates) {
            if (candidate.resolveActivity(pm) != null) {
                try {
                    candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(candidate)
                    launched = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Candidate intent failed for ${target.displayName}: ${candidate.action}", e)
                }
            }
        }

        val res = if (launched) {
            CommandExecutionResult(
                succeeded = true,
                teluguResponse = "${target.displayName} ఓపెన్ చేశాను బాస్! (Android భద్రతా నియమాల వల్ల డైరెక్ట్ టోగుల్ బదులు సిస్టమ్ ప్యానెల్ తెరిచాను.)"
            )
        } else {
            CommandExecutionResult(
                succeeded = false,
                teluguResponse = "బాస్, ఈ డివైజ్‌లో ${target.displayName} సెట్టింగ్స్ స్క్రీన్ అందుబాటులో లేదు."
            )
        }
        recordFeedback(res)
        return res
    }

    fun performGlobalNavigation(action: GlobalNavigationAction): CommandExecutionResult {
        if (!isPanduAccessibilityEnabled()) {
            refreshState()
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "బాస్, ${action.displayName} చేయడానికి Android Settings లో Pandu Babu Accessibility Service enable చేయండి."
            )
            recordFeedback(res)
            return res
        }
        if (!PanduAccessibilityService.isConnected.value) {
            val res = CommandExecutionResult(
                succeeded = false,
                teluguResponse = "బాస్, Accessibility ON లో ఉంది కానీ service ఇంకా connect కాలేదు. ఒకసారి OFF చేసి ON చేసి మళ్ళీ ప్రయత్నించండి."
            )
            recordFeedback(res)
            return res
        }
        val ok = PanduAccessibilityService.performGlobalNav(action)
        val res = if (ok) {
            CommandExecutionResult(
                succeeded = true,
                teluguResponse = "${action.displayName} యాక్షన్ చేశాను బాస్!"
            )
        } else {
            CommandExecutionResult(
                succeeded = false,
                teluguResponse = "${action.displayName} యాక్షన్ ఫెయిల్ అయింది బాస్."
            )
        }
        recordFeedback(res)
        return res
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun recordFeedback(result: CommandExecutionResult) {
        _state.update {
            it.copy(
                lastFeedbackMessage = result.teluguResponse,
                lastFeedbackSuccess = result.succeeded
            )
        }
    }

    fun release() {
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        batteryMonitor.removeListener(batteryListener)
        batteryMonitor.stopMonitoring()
    }
}
