package com.onvexa.recorder.app.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.ui.common.EmptyState
import com.onvexa.recorder.app.ui.common.displayTitle
import com.onvexa.recorder.core.database.entity.ActionItemRow
import com.onvexa.recorder.core.design.OnvexaPalette

@Composable
fun TasksScreen(onOpenRecording: (String) -> Unit, viewModel: TasksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = stringResource(R.string.tasks_title),
            style = MaterialTheme.typography.headlineMedium,
            color = OnvexaPalette.Text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (state.loaded && state.open.isEmpty() && state.done.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CheckCircle,
                title = stringResource(R.string.tasks_empty_title),
                subtitle = stringResource(R.string.tasks_empty_subtitle),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.open.isNotEmpty()) {
                item(key = "open-header") {
                    Text(
                        stringResource(R.string.tasks_pending, state.open.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = OnvexaPalette.TextMuted,
                    )
                }
                items(state.open, key = { it.id }) { row ->
                    TaskRow(row, onToggle = { viewModel.setDone(row.id, it) }, onOpen = { onOpenRecording(row.recordingId) })
                }
            }
            if (state.done.isNotEmpty()) {
                item(key = "done-header") {
                    Text(
                        stringResource(R.string.tasks_done, state.done.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = OnvexaPalette.TextMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(state.done, key = { it.id }) { row ->
                    TaskRow(row, onToggle = { viewModel.setDone(row.id, it) }, onOpen = { onOpenRecording(row.recordingId) })
                }
            }
        }
    }
}

@Composable
private fun TaskRow(row: ActionItemRow, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val done = row.status == "done"
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onOpen),
        shape = shape,
        color = OnvexaPalette.Surface,
        border = BorderStroke(1.dp, OnvexaPalette.Outline),
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = done,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = OnvexaPalette.Primary,
                    uncheckedColor = OnvexaPalette.TextMuted,
                    checkmarkColor = OnvexaPalette.OnPrimary,
                ),
            )
            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) OnvexaPalette.TextMuted else OnvexaPalette.Text,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                val owner = row.assignee ?: stringResource(R.string.task_unassigned)
                val due = row.dueDate?.let { stringResource(R.string.task_due, it) }
                Text(
                    text = listOfNotNull(owner, due).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnvexaPalette.TextMuted,
                )
                Text(
                    text = displayTitle(row.recordingTitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnvexaPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
