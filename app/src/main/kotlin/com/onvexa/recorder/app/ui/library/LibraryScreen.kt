package com.onvexa.recorder.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.ui.common.EmptyState
import com.onvexa.recorder.app.ui.common.SourceBadge
import com.onvexa.recorder.app.ui.common.StatusPill
import com.onvexa.recorder.app.ui.common.TagChip
import com.onvexa.recorder.app.ui.common.dayLabel
import com.onvexa.recorder.app.ui.common.displayTitle
import com.onvexa.recorder.app.ui.common.formatTimeOfDay
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.LibraryItem
import com.onvexa.recorder.core.model.Source
import com.onvexa.recorder.core.model.formatClock

@Composable
fun LibraryScreen(
    onOpen: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnvexaPalette.Text,
                    modifier = Modifier.weight(1f),
                )
                if (state.total > 0) {
                    Text(
                        text = stringResource(R.string.library_count, state.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnvexaPalette.TextMuted,
                    )
                }
            }
            SearchField(
                value = query,
                onChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                onClear = {
                    query = ""
                    viewModel.setQuery("")
                },
            )
        }

        FolderRow(state, viewModel)
        FilterRow(state, viewModel)

        when {
            state.loaded && state.total == 0 -> EmptyState(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.library_empty_title),
                subtitle = stringResource(R.string.library_empty_subtitle),
            )
            state.loaded && state.shown == 0 -> EmptyState(
                icon = Icons.Default.Search,
                title = stringResource(R.string.library_no_results_title),
                subtitle = stringResource(R.string.library_no_results_subtitle),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header-${group.date}") {
                        Text(
                            text = dayLabel(group.date),
                            style = MaterialTheme.typography.labelLarge,
                            color = OnvexaPalette.TextMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(group.items, key = { it.id }) { item ->
                        RecordingCard(item = item, onClick = { onOpen(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text(stringResource(R.string.library_search_hint), color = OnvexaPalette.TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnvexaPalette.TextMuted) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.library_clear_search),
                        tint = OnvexaPalette.TextMuted,
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OnvexaPalette.Text,
            unfocusedTextColor = OnvexaPalette.Text,
            focusedBorderColor = OnvexaPalette.PrimaryLight,
            unfocusedBorderColor = OnvexaPalette.Outline,
            focusedContainerColor = OnvexaPalette.Surface,
            unfocusedContainerColor = OnvexaPalette.Surface,
            cursorColor = OnvexaPalette.PrimaryLight,
        ),
    )
}

@Composable
private fun FolderRow(state: LibraryUiState, viewModel: LibraryViewModel) {
    if (state.folders.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.filter.folderId == null,
                onClick = { viewModel.selectFolder(null) },
                label = { Text(stringResource(R.string.filter_all_folders)) },
                colors = chipColors(),
            )
        }
        items(state.folders, key = { it.id }) { folder ->
            FilterChip(
                selected = state.filter.folderId == folder.id,
                onClick = { viewModel.selectFolder(folder.id) },
                label = { Text(folder.name) },
                leadingIcon = {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.width(18.dp))
                },
                colors = chipColors(),
            )
        }
    }
}

@Composable
private fun FilterRow(state: LibraryUiState, viewModel: LibraryViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.filter.source == Source.WATCH,
                onClick = { viewModel.toggleSource(Source.WATCH) },
                label = { Text(stringResource(R.string.filter_watch)) },
                colors = chipColors(),
            )
        }
        item {
            FilterChip(
                selected = state.filter.source == Source.PHONE,
                onClick = { viewModel.toggleSource(Source.PHONE) },
                label = { Text(stringResource(R.string.filter_phone)) },
                colors = chipColors(),
            )
        }
        item {
            FilterChip(
                selected = state.datePreset == DatePreset.TODAY,
                onClick = { viewModel.togglePreset(DatePreset.TODAY) },
                label = { Text(stringResource(R.string.filter_today)) },
                colors = chipColors(),
            )
        }
        item {
            FilterChip(
                selected = state.datePreset == DatePreset.WEEK,
                onClick = { viewModel.togglePreset(DatePreset.WEEK) },
                label = { Text(stringResource(R.string.filter_week)) },
                colors = chipColors(),
            )
        }
        item {
            FilterChip(
                selected = state.datePreset == DatePreset.MONTH,
                onClick = { viewModel.togglePreset(DatePreset.MONTH) },
                label = { Text(stringResource(R.string.filter_month)) },
                colors = chipColors(),
            )
        }
        items(state.tags, key = { "tag-$it" }) { tag ->
            FilterChip(
                selected = state.filter.tag == tag,
                onClick = { viewModel.toggleTag(tag) },
                label = { Text("#$tag") },
                colors = chipColors(),
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    labelColor = OnvexaPalette.TextMuted,
    selectedContainerColor = OnvexaPalette.SurfaceHigh,
    selectedLabelColor = OnvexaPalette.PrimaryLight,
)

@Composable
private fun RecordingCard(item: LibraryItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = OnvexaPalette.Surface,
        border = BorderStroke(1.dp, OnvexaPalette.Outline),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceBadge(item.source)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = displayTitle(item.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnvexaPalette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.summaryPreview?.let { preview ->
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnvexaPalette.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = formatTimeOfDay(item.startedAt) + "  ·  " + formatClock(item.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnvexaPalette.TextMuted,
                    )
                    Spacer(Modifier.width(10.dp))
                    StatusPill(item.status)
                }
                if (item.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.tags.take(3).forEach { TagChip(it) }
                    }
                }
            }
        }
    }
}
