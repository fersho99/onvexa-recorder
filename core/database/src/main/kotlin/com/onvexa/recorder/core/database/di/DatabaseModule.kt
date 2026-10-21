package com.onvexa.recorder.core.database.di

import android.content.Context
import androidx.room.Room
import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.dao.AttachmentDao
import com.onvexa.recorder.core.database.dao.BookmarkDao
import com.onvexa.recorder.core.database.dao.JobDao
import com.onvexa.recorder.core.database.dao.OrganizationDao
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.dao.SummaryDao
import com.onvexa.recorder.core.database.dao.TranscriptDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun recordingDao(db: AppDatabase): RecordingDao = db.recordingDao()
    @Provides fun transcriptDao(db: AppDatabase): TranscriptDao = db.transcriptDao()
    @Provides fun bookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun summaryDao(db: AppDatabase): SummaryDao = db.summaryDao()
    @Provides fun attachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun organizationDao(db: AppDatabase): OrganizationDao = db.organizationDao()
    @Provides fun jobDao(db: AppDatabase): JobDao = db.jobDao()
}
