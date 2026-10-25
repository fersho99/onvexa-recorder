package com.onvexa.recorder.app.backend

import androidx.room.withTransaction
import com.onvexa.recorder.app.data.BuiltInTemplates
import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.entity.*
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** La funcion devuelve datos; la app los guarda en Room para seguir el mismo camino de sync. */
@Singleton
class AiRepository @Inject constructor(
    private val http: BackendHttp, private val auth: AuthRepository, private val sync: SyncRepository,
    private val local: SyncLocalStore, private val database: AppDatabase, private val scheduler: SyncScheduler,
) {
    private val mutex = Mutex()
    suspend fun summarize(recordingId: String) {
        val recording = database.recordingDao().getById(recordingId) ?: error("recording_not_found")
        execute("summary", recordingId, buildJsonObject { put("templateId", recording.templateId ?: BuiltInTemplates.MEETING) }) { result, requestId ->
            val now = System.currentTimeMillis()
            val id = stableId(requestId, "summary")
            database.summaryDao().upsertSummary(SummaryEntity(id, recordingId, recording.templateId,
                result.getValue("executive").jsonPrimitive.content, result.getValue("detailed").jsonPrimitive.content,
                result.getValue("keyTopics").toString(), result.getValue("decisions").toString(), result.getValue("openQuestions").toString(),
                result["model"]?.jsonPrimitive?.content, result["promptVersion"]?.jsonPrimitive?.content, now, now))
            database.summaryDao().upsertActionItems(result.getValue("actionItems").jsonArray.mapIndexed { index, value ->
                val item = value.jsonObject
                ActionItemEntity(stableId(requestId, "action:$index"), recordingId, id, item.getValue("description").jsonPrimitive.content,
                    item["assignee"]?.jsonPrimitive?.contentOrNull, item["dueDate"]?.jsonPrimitive?.contentOrNull, "open",
                    item["sourceMs"]?.jsonPrimitive?.longOrNull, now, now)
            })
        }
    }
    suspend fun mindMap(recordingId: String) = execute("mind_map", recordingId) { result, requestId ->
        val now = System.currentTimeMillis()
        database.summaryDao().upsertMindMap(MindMapEntity(stableId(requestId,"map"), recordingId,
            result.getValue("tree").toString(), result.getValue("markdown").jsonPrimitive.content, now, now))
    }
    suspend fun ask(recordingId: String?, question: String) {
        require(question.isNotBlank() && question.length <= 8000)
        val history = (if (recordingId == null) local.rows("chat_messages").filter { it["recordingId"] == JsonNull }
            else local.rows("chat_messages").filter { it["recordingId"]?.jsonPrimitive?.contentOrNull == recordingId })
            .filter { it["deletedAt"] == JsonNull && !SyncCodec.isDemo("chat_messages", it) }
            .sortedBy { it.getValue("createdAt").jsonPrimitive.long }.takeLast(20)
        execute("chat", recordingId, buildJsonObject {
            put("question", question.trim())
            put("history", JsonArray(history.map { row -> buildJsonObject { put("role", row.getValue("role")); put("content", row.getValue("content")) } }))
        }) { result, requestId ->
            val now = System.currentTimeMillis()
            database.chatDao().upsert(ChatMessageEntity(stableId(requestId,"question"), recordingId, "user", question.trim(), "[]", now, now))
            database.chatDao().upsert(ChatMessageEntity(stableId(requestId,"answer"), recordingId, "assistant",
                result.getValue("answer").jsonPrimitive.content, result.getValue("citations").toString(), now + 1, now + 1))
        }
    }
    suspend fun describeImage(recordingId: String, attachmentId: String) {
        val image = local.rows("attachments").first { it.getValue("id").jsonPrimitive.content == attachmentId }
        require(image.getValue("recordingId").jsonPrimitive.content == recordingId)
        // La ruta remota puede rellenarse en la sincronizacion inicial.
        sync.sync()
        val uploaded = local.rows("attachments").first { it.getValue("id").jsonPrimitive.content == attachmentId }
        execute("vision", recordingId, buildJsonObject {
            put("imagePath", uploaded.getValue("remoteImagePath")); put("positionMs", uploaded.getValue("positionMs"))
        }) { result, _ -> database.attachmentDao().setDescription(attachmentId, result.getValue("description").jsonPrimitive.content, System.currentTimeMillis()) }
    }
    suspend fun followupEmail(recordingId: String): JsonObject {
        var draft: JsonObject? = null
        execute("followup_email", recordingId) { result, _ -> draft = result }
        return checkNotNull(draft)
    }
    private suspend fun execute(
        task: String, recordingId: String?, extra: JsonObject = buildJsonObject {},
        save: suspend (JsonObject, String) -> Unit,
    ) = mutex.withLock {
        val account = auth.account.value ?: throw BackendException(401, "sign_in_required")
        if (!auth.syncEnabled) throw BackendException(403, "sync_consent_required")
        if (recordingId?.startsWith("demo-") == true) throw BackendException(400, "demo_stays_local")
        sync.sync()
        sync.whilePaused {
            val payload = buildJsonObject {
                put("task", task); put("language", if (Locale.getDefault().language == "en") "en" else "es")
                put("recordingId", recordingId?.let(::JsonPrimitive) ?: JsonNull)
                extra.forEach { (key, value) -> put(key, value) }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val requestKey = "ai:$digest"
            val requestId = local.metadata(requestKey)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also { local.setMetadata(requestKey, it) }
            val result = try {
                http.request("POST", "/functions/v1/ai", payload, auth.token(), mapOf("Idempotency-Key" to requestId)).jsonObject
            } catch (e: BackendException) {
                // Una nueva accion explicita puede reintentar un fallo confirmado.
                // Si se perdio la respuesta o sigue pendiente, conservar la clave para no cobrar dos veces.
                if (e.code in setOf("provider_rate_limited", "provider_auth_error", "provider_model_unavailable",
                        "invalid_provider_output", "request_failed")) local.setMetadata(requestKey, "")
                throw e
            }
            if (auth.account.value?.id != account.id) throw BackendException(401, "session_changed")
            database.withTransaction { save(result, requestId) }
            local.setMetadata(requestKey, "")
        }
        scheduler.schedule()
    }
    private fun stableId(key: String, suffix: String) = UUID.nameUUIDFromBytes("$key:$suffix".toByteArray(Charsets.UTF_8)).toString()
}
