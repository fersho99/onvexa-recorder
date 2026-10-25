package com.onvexa.recorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.ActionItemRow
import com.onvexa.recorder.core.database.entity.AttachmentEntity
import com.onvexa.recorder.core.database.entity.BookmarkEntity
import com.onvexa.recorder.core.database.entity.ChatMessageEntity
import com.onvexa.recorder.core.database.entity.FolderEntity
import com.onvexa.recorder.core.database.entity.MindMapEntity
import com.onvexa.recorder.core.database.entity.NoteEntity
import com.onvexa.recorder.core.database.entity.ProcessingJobEntity
import com.onvexa.recorder.core.database.entity.RecordingTagCrossRef
import com.onvexa.recorder.core.database.entity.SummaryEntity
import com.onvexa.recorder.core.database.entity.TagEntity
import com.onvexa.recorder.core.database.entity.TemplateEntity
import com.onvexa.recorder.core.database.entity.VocabularyTermEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Upsert
    suspend fun upsertAll(bookmarks: List<BookmarkEntity>)

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY positionMs")
    fun observeFor(recordingId: String): Flow<List<BookmarkEntity>>

    @Query("UPDATE bookmarks SET deletedAt = :now, updatedAt = :now, syncState = 2 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface SummaryDao {
    @Upsert
    suspend fun upsertSummary(summary: SummaryEntity)

    @Upsert
    suspend fun upsertActionItems(items: List<ActionItemEntity>)

    @Upsert
    suspend fun upsertMindMap(mindMap: MindMapEntity)

    @Query(
        """
        SELECT * FROM summaries
        WHERE recordingId = :recordingId AND deletedAt IS NULL
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    fun observeLatestSummary(recordingId: String): Flow<SummaryEntity?>

    /** Todos los resúmenes de la grabación (uno por plantilla), del más reciente al más antiguo. */
    @Query("SELECT * FROM summaries WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeSummaries(recordingId: String): Flow<List<SummaryEntity>>

    @Query(
        """
        SELECT a.id AS id, a.recordingId AS recordingId, r.title AS recordingTitle,
               a.description AS description, a.assignee AS assignee, a.dueDate AS dueDate,
               a.status AS status, a.sourceMs AS sourceMs
        FROM action_items a
        JOIN recordings r ON r.id = a.recordingId
        WHERE a.deletedAt IS NULL AND r.deletedAt IS NULL AND a.status != 'dismissed'
        ORDER BY a.dueDate IS NULL, a.dueDate, a.createdAt
        """
    )
    fun observeActionRows(): Flow<List<ActionItemRow>>

    @Query("SELECT * FROM action_items WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeActionItems(recordingId: String): Flow<List<ActionItemEntity>>

    @Query(
        """
        SELECT * FROM action_items
        WHERE status = 'open' AND deletedAt IS NULL
        ORDER BY dueDate IS NULL, dueDate
        """
    )
    fun observeOpenActionItems(): Flow<List<ActionItemEntity>>

    @Query("UPDATE action_items SET status = :status, updatedAt = :now, syncState = 1 WHERE id = :id")
    suspend fun setActionStatus(id: String, status: String, now: Long)

    @Query(
        """
        SELECT * FROM mind_maps
        WHERE recordingId = :recordingId AND deletedAt IS NULL
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    fun observeLatestMindMap(recordingId: String): Flow<MindMapEntity?>
}

@Dao
interface AttachmentDao {
    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY positionMs")
    fun observeFor(recordingId: String): Flow<List<AttachmentEntity>>

    @Query("UPDATE attachments SET description = :description, updatedAt = :now, syncState = 1 WHERE id = :id")
    suspend fun setDescription(id: String, description: String, now: Long)
}

@Dao
interface OrganizationDao {
    @Upsert
    suspend fun upsertFolder(folder: FolderEntity)

    @Upsert
    suspend fun upsertTag(tag: TagEntity)

    @Upsert
    suspend fun upsertTemplates(templates: List<TemplateEntity>)

    @Upsert
    suspend fun addTag(ref: RecordingTagCrossRef)

    @Query("UPDATE recording_tags SET deletedAt = :now, updatedAt = :now, syncState = 2 WHERE recordingId = :recordingId AND tagId = :tagId")
    suspend fun removeTag(recordingId: String, tagId: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM folders WHERE id LIKE :prefix || '%'")
    suspend fun deleteFoldersByIdPrefix(prefix: String)

    @Query("DELETE FROM tags WHERE id LIKE :prefix || '%'")
    suspend fun deleteTagsByIdPrefix(prefix: String)

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT t.* FROM tags t
        JOIN recording_tags rt ON rt.tagId = t.id
        WHERE rt.recordingId = :recordingId AND rt.deletedAt IS NULL AND t.deletedAt IS NULL
        ORDER BY t.name
        """
    )
    fun observeTagsFor(recordingId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM templates WHERE deletedAt IS NULL ORDER BY isBuiltIn DESC, nameEs")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplate(id: String): TemplateEntity?
}

@Dao
interface JobDao {
    @Upsert
    suspend fun upsert(job: ProcessingJobEntity)

    @Query("SELECT * FROM processing_jobs WHERE recordingId = :recordingId ORDER BY createdAt")
    fun observeFor(recordingId: String): Flow<List<ProcessingJobEntity>>

    @Query(
        """
        SELECT * FROM processing_jobs
        WHERE status = 'queued' AND runAfter <= :now
        ORDER BY runAfter LIMIT 1
        """
    )
    suspend fun nextRunnable(now: Long): ProcessingJobEntity?

    @Query(
        """
        UPDATE processing_jobs
        SET status = :status, attempts = :attempts, lastError = :error,
            runAfter = :runAfter, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun update(id: String, status: String, attempts: Int, error: String?, runAfter: Long, now: Long)
}

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY positionMs")
    fun observeFor(recordingId: String): Flow<List<NoteEntity>>
}

@Dao
interface ChatDao {
    @Upsert
    suspend fun upsert(message: ChatMessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeForRecording(recordingId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE recordingId IS NULL AND deletedAt IS NULL ORDER BY createdAt")
    fun observeGlobal(): Flow<List<ChatMessageEntity>>
}

@Dao
interface VocabularyDao {
    @Upsert
    suspend fun upsert(term: VocabularyTermEntity)

    @Query("SELECT * FROM vocabulary_terms WHERE deletedAt IS NULL ORDER BY term")
    fun observeAll(): Flow<List<VocabularyTermEntity>>

    @Query("UPDATE vocabulary_terms SET deletedAt = :now, updatedAt = :now, syncState = 2 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
