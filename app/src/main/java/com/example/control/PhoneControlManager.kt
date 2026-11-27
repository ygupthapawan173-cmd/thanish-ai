package com.example.control

import android.Manifest
import com.example.ai.SmartActionPolicy
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneControlManager(private val context: Context) {

    private val contactsHelper = ContactsHelper(context)

    fun getBatterySummary(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery is at $pct% (${if (bm.isCharging) "charging" else "on battery"})."
    }

    fun getBatteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }

    fun setFlashlight(enabled: Boolean): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return "No rear flashlight found."
            cm.setTorchMode(camId, enabled)
            if (enabled) "Flashlight ON request accepted by Android." else "Flashlight OFF request accepted by Android."
        } catch (e: Exception) {
            "Flashlight error: ${e.message}"
        }
    }

    fun setMediaVolume(percent: Int, showSystemUi: Boolean = true): String = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val clamped = percent.coerceIn(0, 100)
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, ((clamped / 100f) * maxVol).toInt(), if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0)
        val actual = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val actualPct = if(maxVol > 0) (actual * 100f / maxVol).toInt() else 0
        "Media volume is $actualPct% (requested $clamped%)."
    } catch (e: Exception) {
        "Volume error: ${e.message}"
    }

    fun openAppByName(appName: String): String {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { SmartActionPolicy.App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        val matches = SmartActionPolicy.matchApp(appName, apps)
        if (matches.isEmpty()) return "No installed app matched '$appName'."
        if (matches.size > 1) return "Multiple apps matched. Say the exact app name: ${matches.take(5).joinToString { it.label }}."
        val match = matches.single()
        val launch = pm.getLaunchIntentForPackage(match.packageId) ?: return "Unable to launch '${match.label}'."
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Launch requested for ${match.label}."
    }

    fun adjustMediaVolume(direction: String): String {
        if (direction !in setOf("up", "down")) return "Invalid volume direction."
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (direction == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return "Media volume: ${if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else 0}%."
    }

    fun openWebSearch(query: String): String {
        if (query.isBlank()) return "Search query is empty."
        val uri = Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q", query).build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Search opened in browser."
    }

    fun dialForConfirmation(target: String): String {
        val q = target.trim()
        val number = if (q.matches(Regex("[+0-9 ()-]{3,30}"))) q.filter { it.isDigit() || it == '+' }
        else {
            if (!contactsHelper.hasPermission()) return "Contacts permission not granted."
            val matches = contactsHelper.findContacts(q, limit = 10).filter { it.displayName.equals(q, true) }.distinctBy { it.phoneNumber }
            if (matches.size != 1) return "Contact is missing or ambiguous. Say the exact saved contact name or number."
            matches.single().phoneNumber
        }
        if (!number.matches(Regex("[+]?[0-9]{3,20}"))) return "Invalid phone number."
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Dialer opened. Confirm the call yourself."
    }

    fun setAlarm(hour24: Int, minute: Int, label: String): String = try {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour24.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS INFINITY Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        String.format("Alarm request opened for %02d:%02d (%s). Confirm in the clock app.", hour24.coerceIn(0,23), minute.coerceIn(0,59), label.ifBlank { "Alarm" })
    } catch (e: Exception) {
        "Alarm error: ${e.message}"
    }

    fun setTimer(seconds: Int, label: String): String = try {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS INFINITY Timer" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Timer request opened for ${seconds.coerceAtLeast(1)} seconds. Confirm in the clock app."
    } catch (e: Exception) {
        "Timer error: ${e.message}"
    }

    fun lookupContact(nameQuery: String): String {
        if (!contactsHelper.hasPermission()) return "Contacts permission not granted. Enable it in Settings."
        val matches = contactsHelper.findContacts(nameQuery, limit = 5)
        if (matches.isEmpty()) return "No contacts found matching '$nameQuery'."
        return matches.joinToString("; ") { "${it.displayName}: ${it.phoneNumber}" }
    }

    fun callContactOrNumber(contactOrNumber: String): String {
        val resolved = contactsHelper.resolvePhoneOrName(contactOrNumber)
            ?: return "Could not find contact '$contactOrNumber'."
        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (hasCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:${resolved.phoneNumber}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return if (hasCall) "Calling ${resolved.displayName}." else "Opened dialer for ${resolved.displayName}."
    }

    fun openWhatsAppMessage(contactOrNumber: String, message: String): String = try {
        val resolved = if (contactOrNumber.isNotBlank()) contactsHelper.resolvePhoneOrName(contactOrNumber) else null
        val digits = resolved?.phoneNumber?.filter { it.isDigit() }.orEmpty()
        val uri = if (digits.isNotEmpty()) Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        else Uri.parse("https://wa.me/?text=${Uri.encode(message)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (resolved != null) "Opened WhatsApp draft to ${resolved.displayName}." else "Opened WhatsApp draft."
    } catch (e: Exception) {
        "WhatsApp error: ${e.message}"
    }

    fun readUpcomingCalendarEvents(daysAhead: Int = 2): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "Calendar permission not granted. Enable READ_CALENDAR in Settings."
        }
        val now = System.currentTimeMillis()
        val end = now + (daysAhead.coerceIn(1, 14) * 24L * 60L * 60L * 1000L)
        val fmt = SimpleDateFormat("EEE hh:mm a", Locale.getDefault())
        val items = mutableListOf<String>()
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { c ->
                while (c.moveToNext() && items.size < 6) {
                    val title = c.getString(0).orEmpty().ifBlank { "Untitled Event" }
                    val startMs = c.getLong(1)
                    items.add("${fmt.format(Date(startMs))}: $title")
                }
            }
        }
        return if (items.isEmpty()) "No upcoming calendar events in the next $daysAhead day(s)."
        else items.joinToString("\n")
    }

    fun createCalendarEvent(title: String, description: String): String = try {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "JARVIS INFINITY Reminder" })
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened Calendar to save '$title'."
    } catch (e: Exception) {
        "Calendar error: ${e.message}"
    }

    fun composeEmail(to: String, subject: String, body: String): String = try {
        val uri = Uri.parse("mailto:${Uri.encode(to)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Opened email draft to $to."
    } catch (e: Exception) {
        "Email compose error: ${e.message}"
    }

    fun readClipboard(): String = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isBlank()) "Clipboard is empty." else "Clipboard text: ${text.take(600)}"
    } catch (e: Exception) {
        "Clipboard read unavailable: ${e.message}"
    }

    fun writeClipboard(text: String): String = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JARVIS INFINITY", text))
        "Copied to clipboard."
    } catch (e: Exception) {
        "Clipboard write error: ${e.message}"
    }

    fun openSystemSetting(section: String): String {
        val action = when (section.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "internet" -> Settings.ACTION_WIRELESS_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "brightness", "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume", "dnd" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opened $section settings."
    }
}
