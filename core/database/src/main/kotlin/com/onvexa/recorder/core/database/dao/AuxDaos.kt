package com.onvexa.recorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.AttachmentEntity
import com.onvexa.recorder.core.database.entity.BookmarkEntity
import com.onvexa.recorder.core.database.entity.FolderEntity
import com.onvexa.recorder.core.database.entity.MindMapEntity
import com.onvexa.recorder.core.database.entity.ProcessingJobEntity
import com.onvexa.recorder.core.database.entity.RecordingTagCrossRef
import com.onvexa.recorder.core.database.entity.SummaryEntity
import com.onvexa.recorder.core.database.entity.TagEntity
import com.onvexa.recorder.core.database.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bookmarks: List<BookmarkEntity>)

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY positionMs")
    fun observeFor(recordingId: String): Flow<List<BookmarkEntity>>

    @Query("UPDATE bookmarks SET deletedAt = :now, updatedAt = :now, syncState = 2 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: SummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActionItems(items: List<ActionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMindMap(mindMap: MindMapEntity)

    @Query(
        """
        SELECT * FROM summaries
        WHERE recordingId = :recordingId AND deletedAt IS NULL
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    fun observeLatestSummary(recordingId: String): Flow<SummaryEntity?>

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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE recordingId = :recordingId AND deletedAt IS NULL ORDER BY positionMs")
    fun observeFor(recordingId: String): Flow<List<AttachmentEntity>>

    @Query("UPDATE attachments SET description = :description, updatedAt = :now, syncState = 1 WHERE id = :id")
    suspend fun setDescription(id: String, description: String, now: Long)
}

@Dao
interface OrganizationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(templates: List<TemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTag(ref: RecordingTagCrossRef)

    @Query("DELETE FROM recording_tags WHERE recordingId = :recordingId AND tagId = :tagId")
    suspend fun removeTag(recordingId: String, tagId: String)

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT t.* FROM tags t
        JOIN recording_tags rt ON rt.tagId = t.id
        WHERE rt.recordingId = :recordingId AND t.deletedAt IS NULL
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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
