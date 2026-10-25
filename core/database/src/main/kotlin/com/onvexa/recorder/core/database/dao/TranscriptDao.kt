package com.onvexa.recorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.onvexa.recorder.core.database.entity.SpeakerEntity
import com.onvexa.recorder.core.database.entity.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {

    @Upsert
    suspend fun upsertSegments(segments: List<TranscriptSegmentEntity>)

    @Upsert
    suspend fun upsertSpeakers(speakers: List<SpeakerEntity>)

    @Query("SELECT * FROM speakers WHERE recordingId = :recordingId")
    suspend fun getSpeakers(recordingId: String): List<SpeakerEntity>

    @Query("UPDATE transcript_segments SET deletedAt=:now, updatedAt=:now, syncState=2 WHERE recordingId=:id")
    suspend fun markSegmentsDeleted(id: String, now: Long)

    @Query("UPDATE speakers SET deletedAt=:now, updatedAt=:now, syncState=2 WHERE recordingId=:id")
    suspend fun markSpeakersDeleted(id: String, now: Long)

    @Transaction
    suspend fun commitTranscript(id: String, speakers: List<SpeakerEntity>, segments: List<TranscriptSegmentEntity>, now: Long) {
        markSegmentsDeleted(id,now); markSpeakersDeleted(id,now)
        upsertSpeakers(speakers); upsertSegments(segments)
    }

    @Query(
        """
        SELECT * FROM transcript_segments
        WHERE recordingId = :recordingId AND deletedAt IS NULL
        ORDER BY seq
        """
    )
    fun observeSegments(recordingId: String): Flow<List<TranscriptSegmentEntity>>

    @Query(
        """
        SELECT * FROM transcript_segments
        WHERE recordingId = :recordingId AND deletedAt IS NULL
        ORDER BY seq
        """
    )
    suspend fun getSegments(recordingId: String): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM speakers WHERE recordingId = :recordingId AND deletedAt IS NULL")
    fun observeSpeakers(recordingId: String): Flow<List<SpeakerEntity>>

    @Query("UPDATE speakers SET displayName = :name, updatedAt = :now, syncState = 1 WHERE id = :speakerId")
    suspend fun renameSpeaker(speakerId: String, name: String?, now: Long)

    /** Segmento activo para un instante del audio (sincroniza el reproductor con la transcripción). */
    @Query(
        """
        SELECT * FROM transcript_segments
        WHERE recordingId = :recordingId AND deletedAt IS NULL
          AND startMs <= :positionMs
        ORDER BY startMs DESC
        LIMIT 1
        """
    )
    suspend fun segmentAt(recordingId: String, positionMs: Long): TranscriptSegmentEntity?

    @Query("DELETE FROM transcript_segments WHERE recordingId = :recordingId")
    suspend fun deleteSegmentsFor(recordingId: String)

    @Query("DELETE FROM speakers WHERE recordingId = :recordingId")
    suspend fun deleteSpeakersFor(recordingId: String)

    /** Reemplaza transcripción y hablantes de una grabación en una sola transacción. */
    @Transaction
    suspend fun replaceTranscript(
        recordingId: String,
        speakers: List<SpeakerEntity>,
        segments: List<TranscriptSegmentEntity>,
    ) {
        deleteSegmentsFor(recordingId)
        deleteSpeakersFor(recordingId)
        upsertSpeakers(speakers)
        upsertSegments(segments)
    }
}
