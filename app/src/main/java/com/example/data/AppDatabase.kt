package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.security.KeystoreCryptoManager

data class VoiceRoutine(
    val id: Long = 0L,
    val name: String,
    val triggerPhrase: String,
    val actionsSummary: String,
    val stepsJson: String
)

data class AuditLogEntry(
    val id: Long = 0L,
    val category: String,
    val summary: String,
    val status: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Encrypted SQLite persistence retained strictly for Scheduled Automation & Audit Trail (Section 16).
 * Standalone Personal Memory, Location Reminders, and Persistent Chat Archive removed.
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    internal val modelContext = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
    }

    private fun encrypted(value: String) = KeystoreCryptoManager.encrypt(value)
    @Volatile var unreadableEncryptedRows = false
        private set

    private fun readableField(value: String): String? = try {
        KeystoreCryptoManager.decryptStrict(value)
    } catch (_: Exception) {
        unreadableEncryptedRows = true
        null
    }

    override fun onCreate(db: SQLiteDatabase) {
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
        db.execSQL("DROP TABLE IF EXISTS memory_facts")
        db.execSQL("DROP TABLE IF EXISTS location_reminders")
        db.execSQL("DROP TABLE IF EXISTS chat_messages")
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS voice_routines (
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
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    status TEXT NOT NULL,
                    timestamp_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            createIndices(db)
        }
    }

    private fun createIndices(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp_ms)")
    }

    private fun seedDefaultRoutines(db: SQLiteDatabase) {
        val defaults = listOf(
            VoiceRoutine(
                name = "Quiet Night",
                triggerPhrase = "quiet night",
                actionsSummary = "Volume 10% • Flashlight OFF",
                stepsJson = """[{"tool":"set_media_volume","args":{"percent":10}},{"tool":"toggle_flashlight","args":{"enabled":false}}]"""
            ),
            VoiceRoutine(
                name = "Focus Mode",
                triggerPhrase = "focus mode",
                actionsSummary = "Mute Media (0%)",
                stepsJson = """[{"tool":"set_media_volume","args":{"percent":0}}]"""
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

    fun deleteRoutine(id: Long) {
        writableDatabase.delete("voice_routines", "id = ?", arrayOf(id.toString()))
    }

    fun findRoutineByNameOrTrigger(query: String): VoiceRoutine? {
        val q = query.trim().lowercase()
        return getAllRoutines().firstOrNull { r ->
            r.name.lowercase() == q || r.triggerPhrase.lowercase() == q || r.name.lowercase().contains(q) || q.contains(r.triggerPhrase.lowercase())
        }
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
        private const val DB_VERSION = 4
    }
}
