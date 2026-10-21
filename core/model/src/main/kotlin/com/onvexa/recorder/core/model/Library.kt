package com.onvexa.recorder.core.model

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Source {
    WATCH,
    PHONE;

    companion object {
        fun fromKey(key: String): Source = if (key == "watch") WATCH else PHONE
    }
}

/** Estados de una grabación (mismos valores que la columna `status`). */
object States {
    const val RECORDING = "recording"
    const val RECORDED = "recorded"
    const val UPLOADING = "uploading"
    const val UPLOADED = "uploaded"
    const val TRANSCRIBING = "transcribing"
    const val SUMMARIZING = "summarizing"
    const val READY = "ready"
    const val FAILED = "failed"

    val processing = setOf(RECORDED, UPLOADING, UPLOADED, TRANSCRIBING, SUMMARIZING)
}

data class LibraryItem(
    val id: String,
    val title: String,
    val source: Source,
    val status: String,
    val durationMs: Long,
    val startedAt: Long,
    val summaryPreview: String?,
    val tags: List<String>,
    val folderId: String?,
    val language: String?,
    val pendingSync: Boolean = false,
) {
    val isProcessing: Boolean get() = status in States.processing
    val isFailed: Boolean get() = status == States.FAILED
}

data class LibraryFilter(
    val query: String = "",
    val source: Source? = null,
    val tag: String? = null,
    val folderId: String? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || source != null || tag != null || fromMs != null || toMs != null
}

/** Minúsculas y sin acentos, para que "reunion" encuentre "Reunión". */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()

fun List<LibraryItem>.applyFilter(filter: LibraryFilter): List<LibraryItem> {
    val terms = normalizeForSearch(filter.query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    return filter { item ->
        val matchesFields =
            (filter.source == null || item.source == filter.source) &&
                (filter.tag == null || filter.tag in item.tags) &&
                (filter.folderId == null || item.folderId == filter.folderId) &&
                (filter.fromMs == null || item.startedAt >= filter.fromMs) &&
                (filter.toMs == null || item.startedAt <= filter.toMs)
        if (!matchesFields) {
            false
        } else if (terms.isEmpty()) {
            true
        } else {
            val haystack = normalizeForSearch(
                listOfNotNull(item.title, item.summaryPreview).plus(item.tags).joinToString(" "),
            )
            terms.all { term -> term in haystack }
        }
    }
}

data class DayGroup(val date: LocalDate, val items: List<LibraryItem>)

/** Agrupa por día local, de más reciente a más antiguo. */
fun List<LibraryItem>.groupByDay(zone: ZoneId): List<DayGroup> =
    sortedByDescending { it.startedAt }
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .map { (date, items) -> DayGroup(date, items) }
