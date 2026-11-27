package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.security.KeystoreCryptoManager
import com.example.security.PortableBackupCipher
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** User-selected encrypted export; transactional non-destructive merge. No credentials or schedule approvals. */
object PortableDataBackup {
    private data class Table(val name: String, val numeric: List<String>, val text: List<String>, val encrypted: Set<String>) {
        val columns = numeric + text
    }
    private val tables = listOf(
        Table("chat_messages", listOf("id","is_from_user","is_voice_input","has_image","is_error","timestamp_ms"), listOf("text","audio_duration","executed_tools"), setOf("text","executed_tools")),
        Table("memory_facts", listOf("id","updated_at_ms"), listOf("fact_key","fact_value"), setOf("fact_value")),
        Table("voice_routines", listOf("id"), listOf("name","trigger_phrase","actions_summary","steps_json"), setOf("actions_summary","steps_json")),
        Table("location_reminders", listOf("id","created_at_ms"), listOf("place_label","reminder_text"), setOf("place_label","reminder_text")),
        Table("audit_logs", listOf("id","timestamp_ms"), listOf("category","summary","status"), setOf("summary"))
    )
    private const val MAX_ROWS = 30_000
    suspend fun export(context: Context, uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        var count = 0
        var textBytes = 0L
        val payload = JSONObject().put("version",1)
        AppDatabase(context).use { helper ->
            val db = helper.readableDatabase
            db.beginTransaction()
            try {
                for (t in tables) {
                    val rows = JSONArray()
                    db.query(t.name,t.columns.toTypedArray(),null,null,null,null,"id ASC").use { c ->
                        while(c.moveToNext()) {
                            require(++count <= MAX_ROWS) { "Backup row limit exceeded" }
                            val row = JSONObject()
                            for (name in t.numeric) row.put(name,c.getLong(c.getColumnIndexOrThrow(name)))
                            for (name in t.text) {
                                val s = c.getString(c.getColumnIndexOrThrow(name))
                                val value=if(name in t.encrypted) KeystoreCryptoManager.decryptStrict(s) else s
                                textBytes += value.toByteArray(Charsets.UTF_8).size
                                require(textBytes <= PortableBackupCipher.MAX_PLAINTEXT) { "Backup size limit exceeded" }
                                row.put(name,value)
                            }
                            rows.put(row)
                        }
                    }
                    payload.put(t.name,rows)
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        val plain=payload.toString().toByteArray(Charsets.UTF_8)
        val archive=try { PortableBackupCipher.seal(plain,password) } finally { plain.fill(0) }
        context.contentResolver.openOutputStream(uri,"wt")?.use { it.write(archive) } ?: error("Cannot open backup destination")
        count
    }
    suspend fun restore(context: Context, uri: Uri, password: CharArray): String = withContext(Dispatchers.IO) {
        val archive = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output=ByteArrayOutputStream(); val buf=ByteArray(8192)
            while(true) { val n=stream.read(buf); if(n<0) break; require(output.size()+n <= PortableBackupCipher.MAX_ARCHIVE) { "Backup too large" }; output.write(buf,0,n) }
            output.toByteArray()
        } ?: error("Cannot open backup file")
        val plain=PortableBackupCipher.open(archive,password)
        val payload=try { JSONObject(String(plain,Charsets.UTF_8)) } finally { plain.fill(0) }
        require(payload.getInt("version")==1)
        val rowCount=tables.sumOf { payload.getJSONArray(it.name).length() }
        require(rowCount<=MAX_ROWS)
        var inserted=0; var skipped=0
        AppDatabase(context).use { helper ->
            val db=helper.writableDatabase
            db.beginTransaction()
            try {
                for(t in tables) {
                    val rows=payload.getJSONArray(t.name)
                    for(i in 0 until rows.length()) {
                        val row=rows.getJSONObject(i); val cv=ContentValues()
                        for(name in t.numeric) {
                            val value=row.getLong(name)
                            if(name in setOf("is_from_user","is_voice_input","has_image","is_error")) require(value in 0..1)
                            cv.put(name,value)
                        }
                        for(name in t.text) {
                            val value=row.getString(name); require(value.length<=1_000_000)
                            cv.put(name,if(name in t.encrypted) KeystoreCryptoManager.encrypt(value) else value)
                        }
                        val id = row.getLong("id"); require(id > 0)
                        // Exact duplicates are skipped even if a previous restore assigned a new ID.
                        val numeric = t.numeric.filter { it != "id" }
                        val unique = when(t.name) { "memory_facts" -> listOf("fact_key"); "voice_routines" -> listOf("name"); else -> emptyList() }
                        val where = (numeric + unique).joinToString(" AND ") { "$it = ?" }.ifBlank { null }
                        val args = (numeric.map { row.getLong(it).toString() } + unique.map { row.getString(it) }).takeIf { it.isNotEmpty() }?.toTypedArray()
                        val duplicate = db.query(t.name,t.text.toTypedArray(),where,args,null,null,null).use { c ->
                            var match=false
                            while(c.moveToNext() && !match) {
                                match=t.text.all { name ->
                                    val stored=c.getString(c.getColumnIndexOrThrow(name))
                                    val original=if(name in t.encrypted) runCatching { KeystoreCryptoManager.decryptStrict(stored) }.getOrNull() else stored
                                    original == row.getString(name)
                                }
                            }
                            match
                        }
                        if (duplicate) { skipped++; continue }
                        // New local ID for distinct records. Never overwrite unique key/name or restore schedule approvals.
                        cv.remove("id")
                        if(db.insertWithOnConflict(t.name,null,cv,SQLiteDatabase.CONFLICT_IGNORE)==-1L) skipped++ else inserted++
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        "Restored $inserted rows; skipped $skipped existing/conflicting rows. Reopen JARVIS to refresh views. Schedules were not restored."
    }
}
