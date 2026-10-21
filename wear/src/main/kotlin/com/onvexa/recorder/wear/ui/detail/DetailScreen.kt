package com.onvexa.recorder.wear.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.States
import com.onvexa.recorder.core.model.formatClock
import com.onvexa.recorder.wear.R
import com.onvexa.recorder.wear.data.WatchDetail
import com.onvexa.recorder.wear.ui.common.WearList
import com.onvexa.recorder.wear.ui.common.formatWhen

@Composable
fun DetailScreen(viewModel: DetailViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val detail = ui.detail
    var expanded by rememberSaveable { mutableStateOf(false) }

    WearList {
        if (detail == null) {
            item {
                Text(
                    text = stringResource(R.string.detail_not_found),
                    style = MaterialTheme.typography.body2,
                    color = OnvexaPalette.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@WearList
        }
        val recording = detail.recording
        item {
            Text(
                text = recording.title?.ifBlank { null } ?: stringResource(R.string.untitled),
                style = MaterialTheme.typography.title3,
                color = OnvexaPalette.Text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            Text(
                text = formatWhen(recording.startedAt) + " · " + formatClock(recording.durationMs),
                style = MaterialTheme.typography.caption1,
                color = OnvexaPalette.TextMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (recording.syncState != 0) {
            item {
                Text(
                    text = stringResource(R.string.detail_pending_sync),
                    style = MaterialTheme.typography.caption2,
                    color = OnvexaPalette.Warning,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.detail_summary),
                style = MaterialTheme.typography.caption1,
                color = OnvexaPalette.PrimaryLight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val summary = detail.summary ?: stringResource(summaryFallback(detail))
            val shape = RoundedCornerShape(12.dp)
            Text(
                text = summary,
                style = MaterialTheme.typography.body2,
                color = if (detail.summary == null) OnvexaPalette.TextMuted else OnvexaPalette.Text,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable(enabled = detail.summary != null) { expanded = !expanded }
                    .padding(vertical = 4.dp),
            )
        }
        if (detail.actions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.detail_actions),
                    style = MaterialTheme.typography.caption1,
                    color = OnvexaPalette.PrimaryLight,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            items(detail.actions, key = { it.id }) { action ->
                ActionChip(action) { viewModel.setDone(action.id, !it) }
            }
        }
    }
}

private fun summaryFallback(detail: WatchDetail): Int = when {
    detail.recording.status == States.FAILED -> R.string.detail_failed
    detail.recording.syncState != 0 -> R.string.detail_waiting_sync
    detail.recording.status in States.processing -> R.string.detail_processing
    else -> R.string.detail_no_summary
}

@Composable
private fun ActionChip(action: ActionItemEntity, onToggle: (Boolean) -> Unit) {
    val done = action.status == "done"
    Chip(
        onClick = { onToggle(done) },
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = action.description,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
        },
        icon = {
            Icon(
                imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (done) OnvexaPalette.Success else OnvexaPalette.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = OnvexaPalette.Surface,
            contentColor = if (done) OnvexaPalette.TextMuted else OnvexaPalette.Text,
        ),
    )
}
