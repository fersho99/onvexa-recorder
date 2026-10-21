package com.onvexa.recorder.core.database.entity

import com.onvexa.recorder.core.model.LibraryItem
import com.onvexa.recorder.core.model.Source

/** Convierte una fila de la biblioteca en el modelo que usan las pantallas del celular y del reloj. */
fun RecordingListRow.toLibraryItem() = LibraryItem(
    id = id,
    title = title.orEmpty(),
    source = Source.fromKey(source),
    status = status,
    durationMs = durationMs,
    startedAt = startedAt,
    summaryPreview = summaryPreview,
    tags = tagsCsv?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
    folderId = folderId,
    language = language,
    pendingSync = syncState != SyncState.SYNCED,
)
