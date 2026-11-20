package com.example

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.control.AppControlTarget
import com.example.control.BatteryMonitorHelper
import com.example.control.GlobalNavigationAction
import com.example.control.PhoneControlManager
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(database.chatMessageDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read app name from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Pandu Babu", appName)
    }

    @Test
    fun `chat repository saves and clears telugu messages`() = runTest {
        repository.saveUserMessage("హలో పాండు బాబు!", isVoiceInput = true)
        repository.saveAssistantMessage("నమస్తే బాస్! జార్విస్ రెడీగా ఉన్నాడు!")

        val saved = repository.messages.first()
        assertEquals(2, saved.size)
        assertEquals(ChatMessageEntity.ROLE_USER, saved[0].role)
        assertTrue(saved[0].isVoiceInput)
        assertEquals(ChatMessageEntity.ROLE_ASSISTANT, saved[1].role)

        repository.clearChat()
        val afterClear = repository.messages.first()
        assertTrue(afterClear.isEmpty())
    }

    @Test
    fun `phone and messages require explicit confirmation`() {
        assertTrue(AppControlTarget.PHONE.requiresConfirmation)
        assertTrue(AppControlTarget.MESSAGES.requiresConfirmation)
        assertFalse(AppControlTarget.YOUTUBE.requiresConfirmation)
        assertFalse(AppControlTarget.SETTINGS.requiresConfirmation)
    }

    @Test
    fun `phone control manager reports honest failures without pretending success`() {
        var canWriteBrightness = false
        val manager = PhoneControlManager(context, canWriteSettingsProvider = { canWriteBrightness })
        try {
            val ytResult = manager.openApp(AppControlTarget.YOUTUBE)
            assertFalse(ytResult.succeeded)
            assertTrue(ytResult.teluguResponse.contains("YouTube"))

            val torchResult = manager.setTorchEnabled(true)
            assertFalse(torchResult.succeeded)
            assertFalse(manager.state.value.isTorchOn)

            val brightnessDenied = manager.setSystemBrightnessPercent(75)
            assertFalse(brightnessDenied.succeeded)
            assertTrue(brightnessDenied.teluguResponse.contains("Modify System Settings"))

            canWriteBrightness = true
            val brightnessAllowed = manager.setSystemBrightnessPercent(75)
            assertTrue(brightnessAllowed.succeeded)
            assertEquals(75, manager.state.value.brightnessPercent)

            val navResult = manager.performGlobalNavigation(GlobalNavigationAction.BACK)
            assertFalse(navResult.succeeded)
            assertTrue(navResult.teluguResponse.contains("Accessibility"))
        } finally {
            manager.release()
        }
    }

    @Test
    fun `battery monitor helper parses charging, discharging, and full battery states`() {
        val helper = BatteryMonitorHelper(context)
        try {
            helper.startMonitoring()

            val chargingSticky = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(BatteryManager.EXTRA_LEVEL, 64)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
                putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
                putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
                putExtra(BatteryManager.EXTRA_TEMPERATURE, 335)
                putExtra(BatteryManager.EXTRA_VOLTAGE, 4210)
                putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
            }
            @Suppress("DEPRECATION")
            context.sendStickyBroadcast(chargingSticky)
            val chargingInfo = helper.refresh()

            assertEquals(64, chargingInfo.levelPercent)
            assertTrue(chargingInfo.isCharging)
            assertFalse(chargingInfo.isFull)
            assertEquals("AC Charger", chargingInfo.chargingSource)
            assertEquals(33.5f, chargingInfo.temperatureCelsius, 0.01f)
            assertEquals("Good", chargingInfo.healthLabel)
            assertEquals(chargingInfo, helper.batteryState.value)

            val dischargingSticky = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(BatteryManager.EXTRA_LEVEL, 18)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
                putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
                putExtra(BatteryManager.EXTRA_PLUGGED, 0)
            }
            @Suppress("DEPRECATION")
            context.sendStickyBroadcast(dischargingSticky)
            val dischargingInfo = helper.refresh()

            assertEquals(18, dischargingInfo.levelPercent)
            assertFalse(dischargingInfo.isCharging)
            assertFalse(dischargingInfo.isFull)
            assertEquals("On Battery", dischargingInfo.chargingSource)

            val fullSticky = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(BatteryManager.EXTRA_LEVEL, 100)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
                putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
                putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
            }
            @Suppress("DEPRECATION")
            context.sendStickyBroadcast(fullSticky)
            val fullInfo = helper.refresh()

            assertEquals(100, fullInfo.levelPercent)
            assertTrue(fullInfo.isCharging)
            assertTrue(fullInfo.isFull)
            assertEquals("USB", fullInfo.chargingSource)
        } finally {
            helper.stopMonitoring()
        }
    }
}
