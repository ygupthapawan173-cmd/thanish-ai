package com.example.omega

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.AppDatabase
import com.example.data.OmegaPreferences
import com.example.control.PhoneControlManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

private data class DailySchedule(val name: String, val hour: Int, val minute: Int, val fingerprint: String, val token: String)

class TimeAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val name = intent?.getStringExtra("routine_name") ?: return
        val token = intent.getStringExtra("schedule_token") ?: run {
            TimeAutomationScheduler.cancelLegacy(context.applicationContext, name)
            return // old alarms must be explicitly reapproved
        }
        val pending = goAsync()
        TimeAutomationScheduler.dispatch(context.applicationContext, name, token, pending::finish)
    }
}

class ScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        TimeAutomationScheduler.restore(context.applicationContext)
    }
}

object TimeAutomationScheduler {
    private val executor = Executors.newSingleThreadExecutor()
    private val actionLock = Any()
    private const val QUEUE_TAG = "infinity-daily-queue"
    private fun queueState(context: Context) = context.getSharedPreferences("infinity_queue_state", Context.MODE_PRIVATE)
    private fun epoch(context: Context) = queueState(context).getLong("stop_epoch", 0L)
    private fun tokenTag(token: String) = "infinity-schedule-$token"
    private fun store(context: Context) = context.getSharedPreferences("infinity_daily_schedules_v1", Context.MODE_PRIVATE)
    private fun alarm(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun decode(name: String, raw: String?): DailySchedule? = runCatching {
        val j = JSONObject(requireNotNull(raw))
        DailySchedule(name, j.getInt("hour"), j.getInt("minute"), j.getString("fingerprint"), j.getString("token"))
    }.getOrNull()
    private fun read(context: Context, name: String) = decode(name, store(context).getString(name, null))
    private fun intent(context: Context, name: String) = Intent(context, TimeAutomationReceiver::class.java)
        .setData(Uri.Builder().scheme("infinity").authority("daily").appendPath(name).build())
        .putExtra("routine_name", name)
    internal fun cancelLegacy(context: Context, name: String) {
        val oldIntent = Intent(context, TimeAutomationReceiver::class.java).putExtra("routine_name", name)
        PendingIntent.getBroadcast(context, name.hashCode(), oldIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarm(context).cancel(it); it.cancel() }
    }
    private fun arm(context: Context, s: DailySchedule) {
        val pi = PendingIntent.getBroadcast(context, 0, intent(context, s.name).putExtra("schedule_token", s.token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next = DailySchedulePolicy.nextMillis(System.currentTimeMillis(), s.hour, s.minute, ZoneId.systemDefault())
        alarm(context).set(AlarmManager.RTC_WAKEUP, next, pi) // inexact; Android may defer
    }
    private fun validated(steps: String): List<Pair<String, Any>> {
        val array = JSONArray(steps)
        require(array.length() in 1..8) { "Use 1–8 background steps" }
        return (0 until array.length()).map { i ->
            val step = array.getJSONObject(i)
            val tool = step.getString("tool")
            val args = step.getJSONObject("args")
            val value = args.opt(if (tool == "toggle_flashlight") "enabled" else "percent")
            require(DailySchedulePolicy.eligible(tool, value)) { "$tool requires foreground execution or valid arguments" }
            tool to requireNotNull(value)
        }
    }
    @Synchronized fun scheduleDailyRoutine(context: Context, routineName: String, hour24: Int, minute: Int): String {
        require(routineName.isNotBlank() && hour24 in 0..23 && minute in 0..59)
        val routine = AppDatabase(context).use { db -> db.getAllRoutines().firstOrNull { it.name == routineName } }
            ?: error("Routine not found")
        validated(routine.stepsJson)
        val prefs = OmegaPreferences(context)
        try { require(!prefs.settings.value.biometricSafetyLock) { "Biometric protection requires foreground approval" } } finally { prefs.close() }
        val old = read(context, routineName)
        val s = DailySchedule(routineName, hour24, minute, DailySchedulePolicy.fingerprint(routine.stepsJson), UUID.randomUUID().toString())
        val json = JSONObject().put("hour", s.hour).put("minute", s.minute).put("fingerprint", s.fingerprint).put("token", s.token)
        check(store(context).edit().putString(s.name, json.toString()).commit()) { "Schedule could not be saved" }
        cancelLegacy(context, s.name)
        try { arm(context, s) } catch (e: Exception) { store(context).edit().remove(s.name).commit(); throw e }
        old?.let { WorkManager.getInstance(context).cancelAllWorkByTag(tokenTag(it.token)) }
        return String.format(Locale.ROOT, "Scheduled '%s' around %02d:%02d daily (inexact; may be delayed).", routineName, hour24, minute)
    }
    @Synchronized fun cancel(context: Context, name: String): String {
        val old = read(context, name)
        check(store(context).edit().remove(name).commit()) { "Could not cancel saved schedule" }
        PendingIntent.getBroadcast(context, 0, intent(context, name), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarm(context).cancel(it); it.cancel() }
        cancelLegacy(context, name)
        old?.let { WorkManager.getInstance(context).cancelAllWorkByTag(tokenTag(it.token)) }
        return "Daily schedule cancelled: $name"
    }
    @Synchronized fun stopRunning(context: Context) {
        check(queueState(context).edit().putLong("stop_epoch", epoch(context) + 1).commit()) { "Could not persist emergency stop" }
        WorkManager.getInstance(context).cancelAllWorkByTag(QUEUE_TAG)
    }
    @Synchronized fun restore(context: Context) {
        store(context).all.forEach { (name, raw) -> decode(name, raw as? String)?.let { runCatching { arm(context, it) } } }
    }
    fun status(context: Context, name: String): String? = read(context, name)?.let { String.format(Locale.ROOT, "%02d:%02d daily · inexact", it.hour, it.minute) }
    internal fun dispatch(context: Context, name: String, token: String, finish: () -> Unit) {
        // Snapshot approval epoch at receipt, before waiting for the enqueue executor.
        val receivedEpoch = epoch(context)
        val day = LocalDate.now(ZoneId.systemDefault()).toString()
        executor.execute {
            try {
                synchronized(this) {
                    val s = read(context, name)?.takeIf { it.token == token } ?: return@execute
                    arm(context, s) // always preserve next day's alarm
                    if (!QueueRunPolicy.allowed(token, s.token, receivedEpoch, epoch(context), day,
                            LocalDate.now(ZoneId.systemDefault()).toString(), false)) return@execute
                    val request = OneTimeWorkRequestBuilder<DailyRoutineWorker>()
                        .setInputData(workDataOf("name" to name, "token" to token, "day" to day, "epoch" to receivedEpoch))
                        .addTag(QUEUE_TAG).addTag(tokenTag(token)).build()
                    WorkManager.getInstance(context).enqueueUniqueWork("infinity-$token-$day", ExistingWorkPolicy.KEEP, request)
                        .result.get(5, TimeUnit.SECONDS)
                }
            } catch (e: Exception) {
                runCatching { AppDatabase(context).use { it.logAudit("Scheduled Routine", "$name: queue submission not confirmed", "QUEUE_UNCONFIRMED") } }
            } finally { finish() }
        }
    }
    internal fun runQueued(context: Context, name: String, token: String, day: String, receivedEpoch: Long, stopped: () -> Boolean): Boolean = synchronized(actionLock) {
        fun approved(): Boolean = QueueRunPolicy.allowed(token, read(context, name)?.token, receivedEpoch, epoch(context), day,
            LocalDate.now(ZoneId.systemDefault()).toString(), stopped())
        try {
            val s = read(context, name) ?: return@synchronized true
            var previousIncomplete = false
            val claimed = synchronized(this) claim@ {
                if (!approved()) return@claim false
                val raw = store(context).getString(name, null) ?: return@claim false
                val entry = JSONObject(raw)
                if (entry.optString("lastRunDay") == day) {
                    previousIncomplete = entry.optString("lastRunState") == "STARTED"
                    return@claim false
                }
                // Claim before side effects: process death may leave an incomplete run, never replay it blindly.
                check(store(context).edit().putString(name, entry.put("lastRunDay", day).put("lastRunState", "STARTED").toString()).commit()) { "Could not persist run claim" }
                true
            }
            if (!claimed) {
                if (previousIncomplete) runCatching { AppDatabase(context).use { it.logAudit("Scheduled Routine", "$name: interrupted previous attempt; not replayed", "INTERRUPTED") } }
                return@synchronized !previousIncomplete
            }
            AppDatabase(context).use { db ->
                val prefs = OmegaPreferences(context)
                try {
                    require(!prefs.settings.value.biometricSafetyLock) { "Biometric protection requires foreground approval" }
                    val routine = db.getAllRoutines().firstOrNull { it.name == name } ?: error("Routine removed")
                    require(DailySchedulePolicy.fingerprint(routine.stepsJson) == s.fingerprint) { "Routine changed; schedule again to approve" }
                    val steps = validated(routine.stepsJson)
                    val phone = PhoneControlManager(context)
                    val results = mutableListOf<String>()
                    db.logAudit("Scheduled Routine", name, "STARTED")
                    for ((tool, value) in steps) {
                        check(approved()) { "Stopped, cancelled or expired" }
                        val result = if (tool == "toggle_flashlight") phone.setFlashlight(value as Boolean) else phone.setMediaVolume(value as Int, showSystemUi = false)
                        results += result
                        db.logAudit("Scheduled step", "$name: $result", "RESULT")
                        check(!result.contains("error:", ignoreCase = true) && !result.startsWith("No rear flashlight")) { result }
                    }
                    db.logAudit("Scheduled Routine", "$name: ${results.joinToString(" | ")}", "DISPATCHED")
                    SmartTriggerBus.emit(SmartTriggerEvent("Scheduled Routine", results.joinToString(" | ")))
                } finally { prefs.close() }
            }
            synchronized(this) {
                val raw = store(context).getString(name, null)
                if (raw != null) {
                    val entry = JSONObject(raw)
                    if (entry.optString("token") == token && entry.optString("lastRunDay") == day) {
                        check(store(context).edit().putString(name, entry.put("lastRunState", "DISPATCHED").toString()).commit())
                    }
                }
            }
            true
        } catch (e: Exception) {
            runCatching { AppDatabase(context).use { it.logAudit("Scheduled Routine", "$name: ${e.message}", "BLOCKED_OR_FAILED") } }
            SmartTriggerBus.emit(SmartTriggerEvent("Scheduled Routine", "Not completed: ${e.message}"))
            false
        }
    }
}

class DailyRoutineWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val name = inputData.getString("name") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        val day = inputData.getString("day") ?: return Result.failure()
        val epoch = inputData.getLong("epoch", -1)
        if (epoch < 0) return Result.failure()
        return if (TimeAutomationScheduler.runQueued(applicationContext, name, token, day, epoch) { isStopped }) Result.success() else Result.failure()
    }
}
