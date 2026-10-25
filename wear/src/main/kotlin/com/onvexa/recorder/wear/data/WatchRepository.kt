package com.onvexa.recorder.wear.data

import android.content.Context
import android.os.StatFs
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.dao.SummaryDao
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.RecordingEntity
import com.onvexa.recorder.core.database.entity.toLibraryItem
import com.onvexa.recorder.core.model.LibraryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class WatchDetail(
    val recording: RecordingEntity,
    val summary: String?,
    val actions: List<ActionItemEntity>,
)

@Singleton
class WatchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingDao: RecordingDao,
    private val summaryDao: SummaryDao,
    private val transfer: com.onvexa.recorder.core.audio.WearTransfer,
) {
    fun recent(limit: Int = RECENT_LIMIT): Flow<List<LibraryItem>> =
        recordingDao.observeLibraryRows().map { rows -> rows.take(limit).map { it.toLibraryItem() } }

    fun last(): Flow<LibraryItem?> = recent(1).map { it.firstOrNull() }

    fun detail(id: String): Flow<WatchDetail?> = combine(
        recordingDao.observeById(id),
        summaryDao.observeSummaries(id),
        summaryDao.observeActionItems(id),
    ) { recording, summaries, actions ->
        recording?.let { WatchDetail(it, summaries.firstOrNull()?.executive, actions) }
    }

    suspend fun setActionDone(id: String, done: Boolean) {
        summaryDao.setActionStatus(id, if (done) "done" else "open", System.currentTimeMillis())
        transfer.schedule()
    }

    /** Espacio libre en el almacenamiento interno, en bytes. */
    fun freeBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

    private companion object {
        const val RECENT_LIMIT = 20
    }
}
