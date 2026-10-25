package com.onvexa.recorder.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.dao.AttachmentDao
import com.onvexa.recorder.core.database.dao.BookmarkDao
import com.onvexa.recorder.core.database.dao.ChatDao
import com.onvexa.recorder.core.database.dao.JobDao
import com.onvexa.recorder.core.database.dao.NoteDao
import com.onvexa.recorder.core.database.dao.OrganizationDao
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.dao.SummaryDao
import com.onvexa.recorder.core.database.dao.TranscriptDao
import com.onvexa.recorder.core.database.dao.VocabularyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE recording_tags ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE recording_tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE recording_tags ADD COLUMN deletedAt INTEGER")
                    db.execSQL("CREATE TABLE IF NOT EXISTS sync_metadata (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))")
                }
            }).build()

    @Provides fun recordingDao(db: AppDatabase): RecordingDao = db.recordingDao()
    @Provides fun transcriptDao(db: AppDatabase): TranscriptDao = db.transcriptDao()
    @Provides fun bookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun summaryDao(db: AppDatabase): SummaryDao = db.summaryDao()
    @Provides fun attachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun organizationDao(db: AppDatabase): OrganizationDao = db.organizationDao()
    @Provides fun jobDao(db: AppDatabase): JobDao = db.jobDao()
    @Provides fun noteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun chatDao(db: AppDatabase): ChatDao = db.chatDao()
    @Provides fun vocabularyDao(db: AppDatabase): VocabularyDao = db.vocabularyDao()
}
