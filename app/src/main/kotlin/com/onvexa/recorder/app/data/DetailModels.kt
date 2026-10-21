package com.onvexa.recorder.app.data

import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.AttachmentEntity
import com.onvexa.recorder.core.database.entity.BookmarkEntity
import com.onvexa.recorder.core.database.entity.NoteEntity
import com.onvexa.recorder.core.database.entity.RecordingEntity
import com.onvexa.recorder.core.database.entity.SpeakerEntity
import com.onvexa.recorder.core.database.entity.TagEntity
import com.onvexa.recorder.core.database.entity.TranscriptSegmentEntity
import com.onvexa.recorder.core.model.MindNode

data class SummaryView(
    val id: String,
    val templateName: String?,
    val executive: String,
    val detailed: String,
    val topics: List<String>,
    val decisions: List<String>,
    val questions: List<String>,
)

data class ChatMessageView(
    val id: String,
    val role: String,
    val content: String,
    val citations: List<Citation>,
) {
    val isUser: Boolean get() = role == "user"
}

/** Todo lo que necesita la pantalla de detalle de una grabación. */
data class RecordingDetail(
    val recording: RecordingEntity,
    val tags: List<TagEntity>,
    val summaries: List<SummaryView>,
    val segments: List<TranscriptSegmentEntity>,
    val speakers: List<SpeakerEntity>,
    val actions: List<ActionItemEntity>,
    val mindMap: MindNode?,
    val attachments: List<AttachmentEntity>,
    val bookmarks: List<BookmarkEntity>,
    val notes: List<NoteEntity>,
    val chat: List<ChatMessageView>,
)
