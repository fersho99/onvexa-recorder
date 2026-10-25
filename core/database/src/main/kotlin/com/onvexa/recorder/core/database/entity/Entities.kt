package com.onvexa.recorder.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Convenciones de sincronización (ver supabase/migrations/0001_init_schema.sql):
 *  - IDs UUID (String) generados en el cliente.
 *  - Tiempos en milisegundos epoch UTC.
 *  - deletedAt != null  => borrado lógico.
 *  - syncState controla qué falta por subir; remoteUpdatedAt guarda la versión del servidor.
 */

object SyncState {
    const val SYNCED = 0
    const val PENDING_UPSERT = 1
    const val PENDING_DELETE = 2
}

object RecordingSource {
    const val WATCH = "watch"
    const val PHONE = "phone"
}

object RecordingStatus {
    const val RECORDING = "recording"
    const val RECORDED = "recorded"
    const val UPLOADING = "uploading"
    const val UPLOADED = "uploaded"
    const val TRANSCRIBING = "transcribing"
    const val SUMMARIZING = "summarizing"
    const val READY = "ready"
    const val FAILED = "failed"
}

@Entity(tableName = "folders", indices = [Index("parentId")])
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(
    tableName = "templates",
    indices = [Index(value = ["slug"], unique = true)],
)
data class TemplateEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val nameEs: String,
    val nameEn: String,
    val systemPrompt: String,
    val isBuiltIn: Boolean,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.SYNCED,
)

@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(TemplateEntity::class, ["id"], ["templateId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("folderId"),
        Index("templateId"),
        Index("startedAt"),
        Index("status"),
        Index("syncState"),
    ],
)
data class RecordingEntity(
    @PrimaryKey val id: String,
    val deviceId: String?,
    val folderId: String?,
    val templateId: String?,
    val title: String?,
    /** [RecordingSource] */
    val source: String,
    /** [RecordingStatus] */
    val status: String,
    val language: String?,
    val durationMs: Long,
    val startedAt: Long,
    /** Ruta del archivo local (almacenamiento interno de la app). Null si ya se purgó. */
    val localAudioPath: String?,
    /** Ruta en el bucket 'audio' de Supabase. */
    val remoteAudioPath: String?,
    val audioBytes: Long?,
    val audioSha256: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
    /** Última updated_at conocida en el servidor (para last-write-wins). */
    val remoteUpdatedAt: Long? = null,
)

@Entity(
    tableName = "recording_tags",
    primaryKeys = ["recordingId", "tagId"],
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class RecordingTagCrossRef(
    val recordingId: String,
    val tagId: String,
    val syncState: Int = SyncState.PENDING_UPSERT,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Cursores y versiones del servidor; se confirman en la misma transaccion que las filas. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(@PrimaryKey val key: String, val value: String)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["recordingId", "positionMs"])],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val positionMs: Long,
    val label: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(
    tableName = "speakers",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordingId")],
)
data class SpeakerEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val label: String,
    val displayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SpeakerEntity::class, ["id"], ["speakerId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["recordingId", "seq"], unique = true),
        Index(value = ["recordingId", "startMs"]),
        Index("speakerId"),
    ],
)
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val speakerId: String?,
    val seq: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val language: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
) {
    /** rowid implícito de SQLite; lo necesita la tabla FTS con contenido externo. */
}

/**
 * Índice de búsqueda de texto completo sobre transcript_segments.
 *
 * Nota: se usa FTS4 (soportado de forma nativa por Room) en lugar de FTS5, porque
 * FTS5 no está garantizado en el SQLite del sistema en Android 10. Room mantiene
 * el índice sincronizado con triggers automáticos (contentEntity).
 * El tokenizer unicode61 ignora mayúsculas y acentos básicos (útil en español).
 */
@Fts4(
    contentEntity = TranscriptSegmentEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
@Entity(tableName = "transcript_segments_fts")
data class TranscriptSegmentFts(
    @ColumnInfo(name = "text") val text: String,
)

@Entity(
    tableName = "summaries",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordingId")],
)
data class SummaryEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val templateId: String?,
    val executive: String,
    val detailed: String,
    /** JSON: lista de strings. */
    val keyTopicsJson: String,
    val decisionsJson: String,
    val openQuestionsJson: String,
    val model: String?,
    val promptVersion: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(
    tableName = "action_items",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SummaryEntity::class, ["id"], ["summaryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("recordingId"), Index("summaryId"), Index(value = ["status", "dueDate"])],
)
data class ActionItemEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val summaryId: String?,
    val description: String,
    val assignee: String?,
    /** ISO yyyy-MM-dd. */
    val dueDate: String?,
    /** open | done | dismissed */
    val status: String,
    val sourceMs: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(
    tableName = "mind_maps",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordingId")],
)
data class MindMapEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val treeJson: String,
    val markdown: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["recordingId", "positionMs"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val localImagePath: String?,
    val remoteImagePath: String?,
    val mimeType: String,
    val positionMs: Long,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(
    tableName = "processing_jobs",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordingId"), Index(value = ["status", "runAfter"])],
)
data class ProcessingJobEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    /** transcription | diarization | summary | mind_map | vision */
    val stage: String,
    /** queued | running | succeeded | failed | cancelled */
    val status: String,
    val attempts: Int,
    val maxAttempts: Int,
    val lastError: String?,
    val runAfter: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["recordingId", "positionMs"])],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val positionMs: Long,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

/** Mensajes de "Preguntar". recordingId nulo = conversación global sobre toda la biblioteca. */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordingId"), Index("createdAt")],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val recordingId: String?,
    /** user | assistant */
    val role: String,
    val content: String,
    /** JSON: [{"ms": 12000, "text": "..."}] con los momentos del audio citados. */
    val citationsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)

@Entity(tableName = "vocabulary_terms", indices = [Index(value = ["term"], unique = true)])
data class VocabularyTermEntity(
    @PrimaryKey val id: String,
    val term: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: Int = SyncState.PENDING_UPSERT,
)
