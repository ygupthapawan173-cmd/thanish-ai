package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.security.KeystoreCryptoManager

data class ChatMessageEntity(
    val id: Long = System.nanoTime(),
    val text: String,
    val isFromUser: Boolean,
    val isVoiceInput: Boolean = false,
    val audioDuration: String = "0:04",
    val executedTools: List<String> = emptyList(),
    val hasImageAttachment: Boolean = false,
    val isError: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class MemoryFact(
    val id: Long = 0L,
    val key: String,
    val value: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class VoiceRoutine(
    val id: Long = 0L,
    val name: String,
    val triggerPhrase: String,
    val actionsSummary: String,
    val stepsJson: String
)

data class LocationReminder(
    val id: Long = 0L,
    val placeLabel: String,
    val reminderText: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class AuditLogEntry(
    val id: Long = 0L,
    val category: String,
    val summary: String,
    val status: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class AppDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    internal val modelContext = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Reduce recoverable deleted text. Does not guarantee flash/WAL forensic erasure.
        db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
    }

    private fun encrypted(value: String) = KeystoreCryptoManager.encrypt(value)
    @Volatile var unreadableEncryptedRows = false
        private set
    private fun readableField(value: String): String? = try { KeystoreCryptoManager.decryptStrict(value) }
        catch (_: Exception) { unreadableEncryptedRows = true; null }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                is_from_user INTEGER NOT NULL,
                is_voice_input INTEGER NOT NULL,
                audio_duration TEXT NOT NULL,
                executed_tools TEXT NOT NULL,
                has_image INTEGER NOT NULL,
                is_error INTEGER NOT NULL,
                timestamp_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE memory_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fact_key TEXT NOT NULL UNIQUE,
                fact_value TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE voice_routines (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                trigger_phrase TEXT NOT NULL,
                actions_summary TEXT NOT NULL,
                steps_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE location_reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                place_label TEXT NOT NULL,
                reminder_text TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                summary TEXT NOT NULL,
                status TEXT NOT NULL,
                timestamp_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        createIndices(db)
        seedDefaultRoutines(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..2 && newVersion == 3) { "Unsupported database migration $oldVersion -> $newVersion" }
        createIndices(db)
        // SQLiteOpenHelper's transaction rolls back all changes if encryption or writes fail.
        migrateText(db, "chat_messages", listOf("text", "executed_tools"))
        migrateText(db, "memory_facts", listOf("fact_value"), legacyEncrypted = true)
        migrateText(db, "voice_routines", listOf("actions_summary", "steps_json"))
        migrateText(db, "location_reminders", listOf("place_label", "reminder_text"))
        migrateText(db, "audit_logs", listOf("summary"))
    }

    private fun migrateText(db: SQLiteDatabase, table: String, columns: List<String>, legacyEncrypted: Boolean = false) {
        // Table/column names are constants from the calls above. Bounded batches avoid loading all history.
        var last = Long.MIN_VALUE
        while (true) {
            val rows = mutableListOf<Pair<Long, List<String>>>()
            db.query(table, (listOf("id") + columns).toTypedArray(), "id > ?", arrayOf(last.toString()), null, null, "id ASC", "64").use { c ->
                while (c.moveToNext()) rows.add(c.getLong(0) to columns.indices.map { c.getString(it + 1) })
            }
            if (rows.isEmpty()) break
            for ((id, values) in rows) {
                val cv = ContentValues()
                columns.forEachIndexed { i, column ->
                    val value = values[i]
                    // Preserve original legacy ciphertext, even if its key is unavailable. Plaintext chat may begin with ENC1.
                    cv.put(column, if (legacyEncrypted && value.startsWith("ENC1:")) value else encrypted(value))
                }
                check(db.update(table, cv, "id = ?", arrayOf(id.toString())) == 1) { "Database migration update failed" }
                last = id
            }
        }
    }

    private fun createIndices(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_timestamp ON chat_messages(timestamp_ms)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_updated ON memory_facts(updated_at_ms)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp_ms)")
    }

    private fun seedDefaultRoutines(db: SQLiteDatabase) {
        val defaults = listOf(
            VoiceRoutine(
                name = "Good Night",
                triggerPhrase = "good night",
                actionsSummary = "Volume 10% • Flashlight OFF • 6:30 AM Alarm",
                stepsJson = """[{"tool":"set_media_volume","args":{"percent":10}},{"tool":"toggle_flashlight","args":{"enabled":false}},{"tool":"set_alarm","args":{"hour_24":6,"minute":30,"label":"Morning Wake Up"}}]"""
            ),
            VoiceRoutine(
                name = "Morning Briefing",
                triggerPhrase = "good morning",
                actionsSummary = "Volume 65% • Read Notifications & Calendar",
                stepsJson = """[{"tool":"set_media_volume","args":{"percent":65}},{"tool":"read_notifications","args":{"limit":8}},{"tool":"read_calendar","args":{"days":1}}]"""
            ),
            VoiceRoutine(
                name = "Focus Mode",
                triggerPhrase = "focus mode",
                actionsSummary = "Mute Media (0%) • Open DND Settings",
                stepsJson = """[{"tool":"set_media_volume","args":{"percent":0}},{"tool":"open_system_settings","args":{"section":"dnd"}}]"""
            ),
            VoiceRoutine(
                name = "Emergency Torch",
                triggerPhrase = "torch max",
                actionsSummary = "Flashlight ON • Volume 100%",
                stepsJson = """[{"tool":"toggle_flashlight","args":{"enabled":true}},{"tool":"set_media_volume","args":{"percent":100}}]"""
            )
        )
        defaults.forEach { r ->
            val cv = ContentValues().apply {
                put("name", r.name)
                put("trigger_phrase", r.triggerPhrase.lowercase())
                put("actions_summary", encrypted(r.actionsSummary))
                put("steps_json", encrypted(r.stepsJson))
            }
            db.insertWithOnConflict("voice_routines", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun insertMessage(msg: ChatMessageEntity): Long {
        val cv = ContentValues().apply {
            put("text", encrypted(msg.text))
            put("is_from_user", if (msg.isFromUser) 1 else 0)
            put("is_voice_input", if (msg.isVoiceInput) 1 else 0)
            put("audio_duration", msg.audioDuration)
            put("executed_tools", encrypted(msg.executedTools.joinToString(",")))
            put("has_image", if (msg.hasImageAttachment) 1 else 0)
            put("is_error", if (msg.isError) 1 else 0)
            put("timestamp_ms", msg.timestampMs)
        }
        return writableDatabase.insert("chat_messages", null, cv)
    }

    fun getRecentMessages(limit: Int = 80): List<ChatMessageEntity> {
        val result = mutableListOf<ChatMessageEntity>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM (SELECT * FROM chat_messages ORDER BY id DESC LIMIT ?) ORDER BY id ASC",
            arrayOf(limit.toString())
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val toolsRaw = readableField(c.getString(c.getColumnIndexOrThrow("executed_tools")).orEmpty()) ?: continue
                val messageText = readableField(c.getString(c.getColumnIndexOrThrow("text"))) ?: continue
                result.add(
                    ChatMessageEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        text = messageText,
                        isFromUser = c.getInt(c.getColumnIndexOrThrow("is_from_user")) == 1,
                        isVoiceInput = c.getInt(c.getColumnIndexOrThrow("is_voice_input")) == 1,
                        audioDuration = c.getString(c.getColumnIndexOrThrow("audio_duration")),
                        executedTools = toolsRaw.split(",").filter { it.isNotBlank() },
                        hasImageAttachment = c.getInt(c.getColumnIndexOrThrow("has_image")) == 1,
                        isError = c.getInt(c.getColumnIndexOrThrow("is_error")) == 1,
                        timestampMs = c.getLong(c.getColumnIndexOrThrow("timestamp_ms"))
                    )
                )
            }
        }
        return result
    }

    fun clearMessages() = writableDatabase.delete("chat_messages", null, null)

    fun upsertMemory(key: String, value: String) {
        val k = key.trim().lowercase()
        require(k.isNotEmpty() && value.isNotBlank()) { "Memory key and value required" }
        val cv = ContentValues().apply {
            put("fact_key", k)
            put("fact_value", KeystoreCryptoManager.encrypt(value.trim()))
            put("updated_at_ms", System.currentTimeMillis())
        }
        check(writableDatabase.insertWithOnConflict("memory_facts", null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Memory was not saved" }
    }

    fun deleteMemory(key: String) {
        writableDatabase.delete("memory_facts", "fact_key = ?", arrayOf(key.trim().lowercase()))
    }

    fun getAllMemories(): List<MemoryFact> {
        val list = mutableListOf<MemoryFact>()
        readableDatabase.rawQuery("SELECT * FROM memory_facts ORDER BY updated_at_ms DESC", null).use { c ->
            while (c.moveToNext()) {
                val decryptedVal = readableField(c.getString(c.getColumnIndexOrThrow("fact_value"))) ?: continue
                list.add(
                    MemoryFact(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        key = c.getString(c.getColumnIndexOrThrow("fact_key")),
                        value = decryptedVal,
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms"))
                    )
                )
            }
        }
        return list
    }

    fun formatRelevantMemoriesForPrompt(query: String): String {
        val relevant = SemanticMemoryEngine.search(query, getAllMemories(), topK = 6)
        return if (relevant.isEmpty()) "" else relevant.joinToString("\n") { "- ${it.key}: ${it.value}" }
    }

    fun getAllRoutines(): List<VoiceRoutine> {
        val list = mutableListOf<VoiceRoutine>()
        readableDatabase.rawQuery("SELECT * FROM voice_routines ORDER BY id ASC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    VoiceRoutine(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        triggerPhrase = c.getString(c.getColumnIndexOrThrow("trigger_phrase")),
                        actionsSummary = readableField(c.getString(c.getColumnIndexOrThrow("actions_summary"))) ?: continue,
                        stepsJson = readableField(c.getString(c.getColumnIndexOrThrow("steps_json"))) ?: continue
                    )
                )
            }
        }
        return list
    }

    fun saveRoutine(routine: VoiceRoutine) {
        val cv = ContentValues().apply {
            put("name", routine.name.trim())
            put("trigger_phrase", routine.triggerPhrase.trim().lowercase())
            put("actions_summary", encrypted(routine.actionsSummary.trim()))
            put("steps_json", encrypted(routine.stepsJson))
        }
        writableDatabase.insertWithOnConflict("voice_routines", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteRoutine(id: Long) { writableDatabase.delete("voice_routines", "id = ?", arrayOf(id.toString())) }

    fun findRoutineByNameOrTrigger(query: String): VoiceRoutine? {
        val q = query.trim().lowercase()
        return getAllRoutines().firstOrNull { r ->
            r.name.lowercase() == q || r.triggerPhrase.lowercase() == q || r.name.lowercase().contains(q) || q.contains(r.triggerPhrase.lowercase())
        }
    }

    fun addLocationReminder(place: String, text: String) {
        val cv = ContentValues().apply {
            put("place_label", encrypted(place.trim()))
            put("reminder_text", encrypted(text.trim()))
            put("created_at_ms", System.currentTimeMillis())
        }
        writableDatabase.insert("location_reminders", null, cv)
    }

    fun getLocationReminders(): List<LocationReminder> {
        val list = mutableListOf<LocationReminder>()
        readableDatabase.rawQuery("SELECT * FROM location_reminders ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    LocationReminder(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        placeLabel = readableField(c.getString(c.getColumnIndexOrThrow("place_label"))) ?: continue,
                        reminderText = readableField(c.getString(c.getColumnIndexOrThrow("reminder_text"))) ?: continue,
                        createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at_ms"))
                    )
                )
            }
        }
        return list
    }

    fun logAudit(category: String, summary: String, status: String) {
        val cv = ContentValues().apply {
            put("category", category.trim())
            put("summary", encrypted(summary.trim()))
            put("status", status.trim())
            put("timestamp_ms", System.currentTimeMillis())
        }
        writableDatabase.insert("audit_logs", null, cv)
    }

    fun getRecentAuditLogs(limit: Int = 30): List<AuditLogEntry> {
        val list = mutableListOf<AuditLogEntry>()
        readableDatabase.rawQuery("SELECT * FROM audit_logs ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) {
                list.add(
                    AuditLogEntry(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        summary = readableField(c.getString(c.getColumnIndexOrThrow("summary"))) ?: continue,
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        timestampMs = c.getLong(c.getColumnIndexOrThrow("timestamp_ms"))
                    )
                )
            }
        }
        return list
    }

    companion object {
        private const val DB_NAME = "omega_pandu_v19_auto.db"
        private const val DB_VERSION = 3
    }
}
