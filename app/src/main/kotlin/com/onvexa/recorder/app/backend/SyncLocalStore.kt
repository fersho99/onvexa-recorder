package com.onvexa.recorder.app.backend

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.onvexa.recorder.core.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Singleton
class SyncLocalStore @Inject constructor(private val database: AppDatabase) {
    private fun checkTable(table: String) = require(table in SyncCodec.tables)
    suspend fun rows(table: String, pending: Boolean = false): List<JsonObject> = withContext(Dispatchers.IO) {
        checkTable(table)
        query("SELECT * FROM $table" + if (pending) " WHERE syncState != 0" else "")
    }
    suspend fun metadata(key: String): String? = withContext(Dispatchers.IO) { meta(key) }
    suspend fun applyPurge(table: String, key: String) = withContext(Dispatchers.IO) {
        checkTable(table)
        val keys = SyncCodec.keys(table); val values = key.split(':')
        require(values.size == keys.size && values.all { com.onvexa.recorder.core.audio.MediaFiles.uuid(it) })
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE " + keys.joinToString(" AND ") { "$it=?" },values.toTypedArray())
            saveMeta("purged:$table:$key","1")
        }
    }
    private fun meta(key: String): String? = database.query(SimpleSQLiteQuery("SELECT value FROM sync_metadata WHERE `key` = ?", arrayOf(key))).use {
        if (it.moveToFirst()) it.getString(0) else null
    }
    private fun saveMeta(key: String, value: String) = database.openHelper.writableDatabase.execSQL(
        "INSERT OR REPLACE INTO sync_metadata (`key`, value) VALUES (?, ?)", arrayOf(key, value))

    private fun query(sql: String, args: Array<out Any?> = emptyArray()): List<JsonObject> =
        database.query(SimpleSQLiteQuery(sql, args)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(buildJsonObject {
                    for (i in 0 until cursor.columnCount) put(cursor.getColumnName(i), when (cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> JsonNull
                        android.database.Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(i))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cursor.getDouble(i))
                        else -> JsonPrimitive(cursor.getString(i))
                    })
                })
            }
        }
    private fun local(table: String, keyRow: JsonObject): JsonObject? {
        val keys = SyncCodec.keys(table)
        return query("SELECT * FROM $table WHERE " + keys.joinToString(" AND ") { "$it = ?" },
            keys.map { keyRow.getValue(it).jsonPrimitive.content }.toTypedArray()).firstOrNull()
    }
    private fun write(table: String, row: JsonObject) {
        val columns = row.keys.toList()
        val keys = SyncCodec.keys(table)
        fun scalar(name: String): Any? {
            val value = row.getValue(name)
            return if (value == JsonNull) null else value.jsonPrimitive.let { if (it.isString) it.content else it.longOrNull ?: it.content }
        }
        // Compatible con SQLite antiguo de Android 10 y sin REPLACE sobre datos:
        // las llamadas a write siempre estan dentro de una transaccion Room.
        val db = database.openHelper.writableDatabase
        if (local(table, row) != null) {
            val updates = columns.filterNot { it in keys }
            db.execSQL("UPDATE $table SET ${updates.joinToString(",") { "$it=?" }} WHERE ${keys.joinToString(" AND ") { "$it=?" }}",
                (updates.map(::scalar) + keys.map(::scalar)).toTypedArray())
        } else {
            db.execSQL("INSERT INTO $table (${columns.joinToString(",")}) VALUES (${columns.joinToString(",") { "?" }})", columns.map(::scalar).toTypedArray())
        }
    }
    suspend fun columns(table: String): List<String> = withContext(Dispatchers.IO) {
        checkTable(table)
        query("PRAGMA table_info($table)").map { it.getValue("name").jsonPrimitive.content }
    }
    suspend fun applyRemote(table: String, remote: JsonObject, columns: List<String>) = withContext(Dispatchers.IO) {
        checkTable(table)
        database.withTransaction {
            val keyRow = SyncCodec.decode(remote, SyncCodec.keys(table), null)
            val key = "version:$table:" + SyncCodec.key(table, keyRow)
            val version = remote.getValue("updated_at").jsonPrimitive.content
            val current = local(table, keyRow)
            if (table == "recordings" && current?.get("status")?.jsonPrimitive?.content == "recording") return@withTransaction
            if (current == null || meta(key) != version) {
                write(table, SyncCodec.decode(remote, columns, current))
                saveMeta(key, version)
            }
        }
    }
    suspend fun acknowledge(table: String, snapshot: JsonObject, remote: JsonObject) = withContext(Dispatchers.IO) {
        checkTable(table)
        database.withTransaction {
            val current = local(table, snapshot)
            val version = remote.getValue("updated_at").jsonPrimitive.content
            if (current == snapshot) {
                write(table, JsonObject(current.toMutableMap().apply {
                    put("syncState", JsonPrimitive(0))
                    if ("remoteUpdatedAt" in current) put("remoteUpdatedAt", JsonPrimitive(java.time.Instant.parse(version).toEpochMilli()))
                }))
            }
            saveMeta("version:$table:" + SyncCodec.key(table, snapshot), version)
        }
    }
    suspend fun setMetadata(key: String, value: String) = withContext(Dispatchers.IO) {
        database.withTransaction { saveMeta(key, value) }
    }
    suspend fun updateFile(table: String, snapshot: JsonObject, changes: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        checkTable(table)
        database.withTransaction {
            val current = local(table, snapshot) ?: throw BackendException(409, "local_row_removed")
            if (current != snapshot) throw BackendException(409, "local_row_changed")
            val updated = JsonObject(snapshot + changes)
            write(table, updated)
            updated
        }
    }
}
