package com.onvexa.recorder.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.app.data.RecordingsRepository
import com.onvexa.recorder.core.database.entity.FolderEntity
import com.onvexa.recorder.core.model.DayGroup
import com.onvexa.recorder.core.model.LibraryFilter
import com.onvexa.recorder.core.model.Source
import com.onvexa.recorder.core.model.applyFilter
import com.onvexa.recorder.core.model.groupByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class DatePreset { TODAY, WEEK, MONTH }

data class LibraryUiState(
    val groups: List<DayGroup> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    val datePreset: DatePreset? = null,
    val folders: List<FolderEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val total: Int = 0,
    val shown: Int = 0,
    val loaded: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(repository: RecordingsRepository) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter())
    private val preset = MutableStateFlow<DatePreset?>(null)

    val state: StateFlow<LibraryUiState> = combine(
        repository.libraryItems(),
        filter,
        preset,
        repository.folders(),
        repository.tags(),
    ) { items, f, p, folders, tags ->
        val filtered = items.applyFilter(f)
        LibraryUiState(
            groups = filtered.groupByDay(ZoneId.systemDefault()),
            filter = f,
            datePreset = p,
            folders = folders,
            tags = tags.map { it.name },
            total = items.size,
            shown = filtered.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun selectFolder(folderId: String?) = filter.update { it.copy(folderId = folderId) }

    fun toggleSource(source: Source) =
        filter.update { it.copy(source = if (it.source == source) null else source) }

    fun toggleTag(tag: String) =
        filter.update { it.copy(tag = if (it.tag == tag) null else tag) }

    fun togglePreset(target: DatePreset) {
        val next = if (preset.value == target) null else target
        preset.value = next
        filter.update { it.copy(fromMs = fromMillis(next)) }
    }

    private fun fromMillis(p: DatePreset?): Long? {
        val now = System.currentTimeMillis()
        return when (p) {
            null -> null
            DatePreset.TODAY -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            DatePreset.WEEK -> now - 7 * DAY_MS
            DatePreset.MONTH -> now - 30 * DAY_MS
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
