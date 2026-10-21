package com.onvexa.recorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.onvexa.recorder.core.database.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recording: RecordingEntity)

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: String): Flow<RecordingEntity?>

    @Query(
        """
        SELECT * FROM recordings
        WHERE deletedAt IS NULL
          AND (:folderId IS NULL OR folderId = :folderId)
          AND (:fromMs IS NULL OR startedAt >= :fromMs)
          AND (:toMs IS NULL OR startedAt <= :toMs)
        ORDER BY startedAt DESC
        """
    )
    fun observeLibrary(folderId: String?, fromMs: Long?, toMs: Long?): Flow<List<RecordingEntity>>

    /**
     * Búsqueda de texto completo: coincide en el título o en cualquier segmento de la transcripción.
     * [ftsQuery] debe venir ya saneado (ver FtsQuery.sanitize), p. ej. "reunion* presupuesto*".
     */
    @Query(
        """
        SELECT DISTINCT r.* FROM recordings r
        LEFT JOIN transcript_segments s ON s.recordingId = r.id AND s.deletedAt IS NULL
        WHERE r.deletedAt IS NULL
          AND (
            r.title LIKE '%' || :plain || '%'
            OR s.rowid IN (
              SELECT rowid FROM transcript_segments_fts WHERE transcript_segments_fts MATCH :ftsQuery
            )
          )
        ORDER BY r.startedAt DESC
        """
    )
    fun search(ftsQuery: String, plain: String): Flow<List<RecordingEntity>>

    @Query(
        """
        SELECT r.* FROM recordings r
        JOIN recording_tags rt ON rt.recordingId = r.id
        WHERE rt.tagId = :tagId AND r.deletedAt IS NULL
        ORDER BY r.startedAt DESC
        """
    )
    fun observeByTag(tagId: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE syncState != 0 ORDER BY updatedAt")
    suspend fun getPendingSync(): List<RecordingEntity>

    @Query("UPDATE recordings SET status = :status, updatedAt = :now, syncState = 1 WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long)

    @Query("UPDATE recordings SET localAudioPath = NULL, updatedAt = :now WHERE id = :id")
    suspend fun clearLocalAudio(id: String, now: Long)

    @Query("UPDATE recordings SET deletedAt = :now, updatedAt = :now, syncState = 2 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** Borrado físico de lo que ya se confirmó como eliminado en el servidor. */
    @Query("DELETE FROM recordings WHERE deletedAt IS NOT NULL AND syncState = 0")
    suspend fun purgeDeletedAndSynced()

    @Transaction
    @Query("DELETE FROM recordings")
    suspend fun deleteAll()
}
