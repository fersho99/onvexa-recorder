package com.onvexa.recorder.app.data

import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.dao.AttachmentDao
import com.onvexa.recorder.core.database.dao.BookmarkDao
import com.onvexa.recorder.core.database.dao.ChatDao
import com.onvexa.recorder.core.database.dao.NoteDao
import com.onvexa.recorder.core.database.dao.OrganizationDao
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.dao.SummaryDao
import com.onvexa.recorder.core.database.dao.TranscriptDao
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.ActionItemRow
import com.onvexa.recorder.core.database.entity.AttachmentEntity
import com.onvexa.recorder.core.database.entity.BookmarkEntity
import com.onvexa.recorder.core.database.entity.ChatMessageEntity
import com.onvexa.recorder.core.database.entity.FolderEntity
import com.onvexa.recorder.core.database.entity.NoteEntity
import com.onvexa.recorder.core.database.entity.RecordingEntity
import com.onvexa.recorder.core.database.entity.SpeakerEntity
import com.onvexa.recorder.core.database.entity.SummaryEntity
import com.onvexa.recorder.core.database.entity.TagEntity
import com.onvexa.recorder.core.database.entity.TemplateEntity
import com.onvexa.recorder.core.database.entity.TranscriptSegmentEntity
import com.onvexa.recorder.core.database.entity.toLibraryItem
import com.onvexa.recorder.core.model.LibraryItem
import com.onvexa.recorder.core.model.MindNode
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

@Singleton
class RecordingsRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val summaryDao: SummaryDao,
    private val bookmarkDao: BookmarkDao,
    private val attachmentDao: AttachmentDao,
    private val organizationDao: OrganizationDao,
    private val noteDao: NoteDao,
    private val chatDao: ChatDao,
    private val database: AppDatabase,
) {
    private fun now() = System.currentTimeMillis()

    fun libraryItems(): Flow<List<LibraryItem>> =
        recordingDao.observeLibraryRows().map { rows -> rows.map { it.toLibraryItem() } }

    fun folders(): Flow<List<FolderEntity>> = organizationDao.observeFolders()
    fun tags(): Flow<List<TagEntity>> = organizationDao.observeTags()
    fun templates(): Flow<List<TemplateEntity>> = organizationDao.observeTemplates()
    fun actionRows(): Flow<List<ActionItemRow>> = summaryDao.observeActionRows()

    fun globalChat(): Flow<List<ChatMessageView>> =
        chatDao.observeGlobal().map { list -> list.map { it.toView() } }

    fun detail(id: String): Flow<RecordingDetail?> {
        val head = combine(
            recordingDao.observeById(id),
            organizationDao.observeTagsFor(id),
            summaryDao.observeSummaries(id),
            transcriptDao.observeSegments(id),
            transcriptDao.observeSpeakers(id),
        ) { recording, tags, summaries, segments, speakers ->
            Head(recording, tags, summaries, segments, speakers)
        }
        val extras = combine(
            summaryDao.observeActionItems(id),
            summaryDao.observeLatestMindMap(id),
            attachmentDao.observeFor(id),
            bookmarkDao.observeFor(id),
            noteDao.observeFor(id),
        ) { actions, mindMap, attachments, bookmarks, notes ->
            Extras(actions, mindMap?.let { parseMindMap(it.treeJson) }, attachments, bookmarks, notes)
        }
        return combine(
            head,
            extras,
            chatDao.observeForRecording(id),
            organizationDao.observeTemplates(),
        ) { h, e, chat, templates ->
            val recording = h.recording ?: return@combine null
            val english = Locale.getDefault().language == "en"
            val names = templates.associate { it.id to if (english) it.nameEn else it.nameEs }
            RecordingDetail(
                recording = recording,
                tags = h.tags,
                summaries = h.summaries.map { it.toView(names[it.templateId]) },
                segments = h.segments,
                speakers = h.speakers,
                actions = e.actions,
                mindMap = e.mindMap,
                attachments = e.attachments,
                bookmarks = e.bookmarks,
                notes = e.notes,
                chat = chat.map { it.toView() },
            )
        }
    }

    suspend fun rename(id: String, title: String) = recordingDao.rename(id, title.trim(), now())

    suspend fun deleteRecording(id: String) = withContext(Dispatchers.IO) { database.withTransaction {
        val timestamp = now()
        recordingDao.softDelete(id,timestamp)
        for (table in listOf("recording_tags","bookmarks","speakers","transcript_segments","summaries","action_items","mind_maps","attachments","notes","chat_messages")) {
            database.openHelper.writableDatabase.execSQL("UPDATE $table SET deletedAt=?,updatedAt=?,syncState=2 WHERE recordingId=? AND deletedAt IS NULL",arrayOf(timestamp,timestamp,id))
        }
    } }

    suspend fun setActionDone(id: String, done: Boolean) =
        summaryDao.setActionStatus(id, if (done) "done" else "open", now())

    suspend fun renameSpeaker(id: String, name: String) =
        transcriptDao.renameSpeaker(id, name.trim().ifEmpty { null }, now())

    /** Borra todos los datos locales (no toca la nube; eso llega con las cuentas). */
    suspend fun deleteAllLocalData() = withContext(Dispatchers.IO) { database.clearAllTables() }

    private class Head(
        val recording: RecordingEntity?,
        val tags: List<TagEntity>,
        val summaries: List<SummaryEntity>,
        val segments: List<TranscriptSegmentEntity>,
        val speakers: List<SpeakerEntity>,
    )

    private class Extras(
        val actions: List<ActionItemEntity>,
        val mindMap: MindNode?,
        val attachments: List<AttachmentEntity>,
        val bookmarks: List<BookmarkEntity>,
        val notes: List<NoteEntity>,
    )
}

private fun SummaryEntity.toView(templateName: String?) = SummaryView(
    id = id,
    templateName = templateName,
    executive = executive,
    detailed = detailed,
    topics = parseStringList(keyTopicsJson),
    decisions = parseStringList(decisionsJson),
    questions = parseStringList(openQuestionsJson),
)

private fun ChatMessageEntity.toView() = ChatMessageView(id, role, content, parseCitations(citationsJson))
