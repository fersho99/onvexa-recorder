package com.onvexa.recorder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.onvexa.recorder.core.database.dao.AttachmentDao
import com.onvexa.recorder.core.database.dao.BookmarkDao
import com.onvexa.recorder.core.database.dao.JobDao
import com.onvexa.recorder.core.database.dao.OrganizationDao
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.dao.SummaryDao
import com.onvexa.recorder.core.database.dao.TranscriptDao
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.AttachmentEntity
import com.onvexa.recorder.core.database.entity.BookmarkEntity
import com.onvexa.recorder.core.database.entity.FolderEntity
import com.onvexa.recorder.core.database.entity.MindMapEntity
import com.onvexa.recorder.core.database.entity.ProcessingJobEntity
import com.onvexa.recorder.core.database.entity.RecordingEntity
import com.onvexa.recorder.core.database.entity.RecordingTagCrossRef
import com.onvexa.recorder.core.database.entity.SpeakerEntity
import com.onvexa.recorder.core.database.entity.SummaryEntity
import com.onvexa.recorder.core.database.entity.TagEntity
import com.onvexa.recorder.core.database.entity.TemplateEntity
import com.onvexa.recorder.core.database.entity.TranscriptSegmentEntity
import com.onvexa.recorder.core.database.entity.TranscriptSegmentFts

@Database(
    entities = [
        FolderEntity::class,
        TagEntity::class,
        TemplateEntity::class,
        RecordingEntity::class,
        RecordingTagCrossRef::class,
        BookmarkEntity::class,
        SpeakerEntity::class,
        TranscriptSegmentEntity::class,
        TranscriptSegmentFts::class,
        SummaryEntity::class,
        ActionItemEntity::class,
        MindMapEntity::class,
        AttachmentEntity::class,
        ProcessingJobEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun summaryDao(): SummaryDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun organizationDao(): OrganizationDao
    abstract fun jobDao(): JobDao

    companion object {
        const val NAME = "recorder.db"
    }
}
