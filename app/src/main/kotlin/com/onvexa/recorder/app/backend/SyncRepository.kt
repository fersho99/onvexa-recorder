package com.onvexa.recorder.app.backend

import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class SyncStatus(val running: Boolean = false, val lastSuccess: Long? = null, val error: String? = null)

@Singleton
class SyncRepository @Inject constructor(
    private val http: BackendHttp, private val auth: AuthRepository,
    private val local: SyncLocalStore, private val storage: StorageTransfer,
    private val media: MediaRepository,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status = _status.asStateFlow()
    suspend fun <T> whilePaused(action: suspend () -> T): T = mutex.withLock { action() }
    suspend fun sync() = mutex.withLock {
        val user = auth.account.value ?: return@withLock
        if (!auth.syncEnabled) return@withLock
        _status.value = _status.value.copy(running = true, error = null)
        try {
            // Releer desde cero evita perder una purga cuya transaccion haya confirmado tarde.
            var sequence = 0L; var tombstoneCount = 0
            while (true) {
                val rows = http.request("POST","/rest/v1/rpc/sync_tombstones",buildJsonObject { put("after_sequence",sequence) },auth.token()).jsonArray
                for (value in rows) {
                    val row = value.jsonObject
                    val table = row.getValue("table_name").jsonPrimitive.content
                    val key = row.getValue("row_key").jsonPrimitive.content
                    media.removePurgedFiles(table,key)
                    local.applyPurge(table,key)
                    sequence = row.getValue("sequence").jsonPrimitive.long
                }
                tombstoneCount += rows.size
                if (rows.size < 500) break
                if (tombstoneCount > 100_000) throw BackendException(413,"sync_page_limit")
            }
            // 0004 crea ai_usage en la misma transaccion que las reglas de integridad.
            // Evitar subir filas contra el esquema inicial aun sin actualizar.
            try {
                http.request("GET", "/rest/v1/ai_usage?select=id&limit=0", token = auth.token())
            } catch (e: BackendException) {
                if (e.status == 404 || e.code in listOf("42P01", "PGRST205")) throw BackendException(409, "backend_upgrade_required")
                throw e
            }
            for (table in SyncCodec.tables) {
                if (auth.account.value?.id != user.id) throw BackendException(401, "session_changed")
                val columns = local.columns(table)
                pull(table, columns)
                var pending = local.rows(table, pending = true).filterNot { SyncCodec.isDemo(table, it) }
                if (table == "folders") pending = orderFolders(pending)
                for (row in pending) {
                    val active = com.onvexa.recorder.core.audio.CaptureRuntime.state.value.id
                    if (active != null && row["recordingId"]?.jsonPrimitive?.contentOrNull == active && com.onvexa.recorder.core.audio.CaptureRuntime.state.value.phase != com.onvexa.recorder.core.audio.CapturePhase.Idle) continue
                    if (table == "recordings" && row["status"]?.jsonPrimitive?.content == "recording") continue
                    if (table == "templates" && row["isBuiltIn"]?.jsonPrimitive?.intOrNull == 1) continue
                    var snapshot = row
                    val deleted = row["deletedAt"] != null && row["deletedAt"] != JsonNull
                    if (!deleted && table == "recordings") {
                        // device_id puede venir del reloj; registrar antes de subir su grabacion.
                        row["deviceId"]?.jsonPrimitive?.contentOrNull?.let { deviceId ->
                            http.request("POST", "/rest/v1/devices?on_conflict=id", buildJsonObject {
                                put("id", deviceId); put("user_id", user.id)
                                put("kind", if (row["source"]?.jsonPrimitive?.content == "watch") "watch" else "phone")
                                put("name", "Onvexa"); put("last_seen_at", Instant.now().toString())
                            }, auth.token(), mapOf("Prefer" to "resolution=merge-duplicates"))
                        }
                        val path = row["localAudioPath"]?.jsonPrimitive?.contentOrNull
                        if (path != null && row["remoteAudioPath"] == JsonNull && row["status"]?.jsonPrimitive?.content !in listOf("recording","transcribing")) {
                            val extension = java.io.File(path).extension.lowercase().takeIf { it in listOf("m4a","opus","wav") } ?: throw BackendException(400,"unsupported_audio_format")
                            val mime = when(extension) { "m4a" -> "audio/mp4"; "wav" -> "audio/wav"; else -> "audio/ogg" }
                            val uploaded = storage.upload("audio", "${user.id}/${row.getValue("id").jsonPrimitive.content}.$extension", path, mime)
                            snapshot = local.updateFile(table, row, buildJsonObject {
                                put("remoteAudioPath", uploaded.path); put("audioBytes", uploaded.bytes); put("audioSha256", uploaded.sha256)
                            })
                        }
                    }
                    if (!deleted && table == "attachments" && row["remoteImagePath"] == JsonNull) {
                        val path = row["localImagePath"]?.jsonPrimitive?.contentOrNull ?: throw BackendException(400, "image_file_missing")
                        val remote = "${user.id}/${row.getValue("recordingId").jsonPrimitive.content}/${row.getValue("id").jsonPrimitive.content}.jpg"
                        val uploaded = storage.upload("attachments", remote, path, row.getValue("mimeType").jsonPrimitive.content)
                        snapshot = local.updateFile(table, row, buildJsonObject { put("remoteImagePath", uploaded.path) })
                    }
                    val conflict = SyncCodec.keys(table).joinToString(",") { SyncCodec.remoteName(it) }
                    val response = http.request("POST", "/rest/v1/$table?on_conflict=$conflict", SyncCodec.encode(snapshot, user.id), auth.token(),
                        mapOf("Prefer" to "resolution=merge-duplicates,return=representation")).jsonArray.single().jsonObject
                    local.acknowledge(table, snapshot, response)
                }
            }
            _status.value = SyncStatus(lastSuccess = System.currentTimeMillis())
        } catch (e: Exception) {
            _status.value = _status.value.copy(running = false, error = if (e is BackendException) e.code else "sync_failed")
            throw e
        }
    }
    private suspend fun pull(table: String, columns: List<String>) {
        val checkpoint = local.metadata("cursor:$table")
        // Releer una ventana para cubrir commits tardios; las versiones evitan pisar cambios locales.
        val start = checkpoint?.let { Instant.parse(it).minusSeconds(300).toString() }
        val keys = SyncCodec.keys(table).map(SyncCodec::remoteName)
        var last: JsonObject? = null; var high = checkpoint
        val received = mutableListOf<JsonObject>()
        while (true) {
            val params = mutableListOf("select=*", "order=updated_at.asc," + keys.joinToString(",") { "$it.asc" }, "limit=500")
            if (last == null) { if (start != null) params.add("updated_at=gte." + encode(start)) }
            else {
                val pageLast = checkNotNull(last)
                val timestamp = pageLast.getValue("updated_at").jsonPrimitive.content
                val branches = mutableListOf("updated_at.gt.$timestamp")
                for (i in keys.indices) branches.add("and(updated_at.eq.$timestamp," +
                    keys.take(i).joinToString("") { "$it.eq.${pageLast.getValue(it).jsonPrimitive.content}," } +
                    "${keys[i]}.gt.${pageLast.getValue(keys[i]).jsonPrimitive.content})")
                params.add("or=" + encode("(" + branches.joinToString(",") + ")"))
            }
            val rows = http.request("GET", "/rest/v1/$table?" + params.joinToString("&"), token = auth.token()).jsonArray.map { it.jsonObject }
            received.addAll(rows)
            if (rows.isNotEmpty()) {
                last = rows.last()
                val newest = last.getValue("updated_at").jsonPrimitive.content
                if (high == null || Instant.parse(newest) > Instant.parse(high)) high = newest
            }
            if (rows.size < 500) break
            if (received.size > 100_000) throw BackendException(413, "sync_page_limit")
        }
        val ordered = if (table == "folders") orderFolders(received, remote = true) else received
        for (row in ordered) local.applyRemote(table, row, columns)
        if (high != null) local.setMetadata("cursor:$table", high)
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun orderFolders(rows: List<JsonObject>, remote: Boolean = false): List<JsonObject> {
        val byId = rows.associateBy { it.getValue("id").jsonPrimitive.content }
        val visited = mutableSetOf<String>(); val active = mutableSetOf<String>(); val ordered = mutableListOf<JsonObject>()
        fun visit(row: JsonObject) {
            val id = row.getValue("id").jsonPrimitive.content
            if (id in visited) return
            if (!active.add(id)) throw BackendException(409, "folder_cycle")
            row[if (remote) "parent_id" else "parentId"]?.jsonPrimitive?.contentOrNull?.let { byId[it]?.let(::visit) }
            active.remove(id); visited.add(id); ordered.add(row)
        }
        rows.forEach(::visit)
        return ordered
    }
}
