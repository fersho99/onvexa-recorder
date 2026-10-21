package com.onvexa.recorder.core.database.entity

/** Fila de la biblioteca: la grabación con un adelanto del resumen y sus etiquetas. */
data class RecordingListRow(
    val id: String,
    val title: String?,
    val source: String,
    val status: String,
    val durationMs: Long,
    val startedAt: Long,
    val folderId: String?,
    val language: String?,
    val summaryPreview: String?,
    /** Nombres de etiquetas separados por "|". */
    val tagsCsv: String?,
    /** 0 = sincronizada; otra cosa = tiene cambios pendientes de subir. */
    val syncState: Int,
)

/** Punto de acción con el título de su grabación, para la pantalla global de tareas. */
data class ActionItemRow(
    val id: String,
    val recordingId: String,
    val recordingTitle: String?,
    val description: String,
    val assignee: String?,
    val dueDate: String?,
    val status: String,
    val sourceMs: Long?,
)
