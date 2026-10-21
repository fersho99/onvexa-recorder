package com.onvexa.recorder.wear.ui.list

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.LibraryItem
import com.onvexa.recorder.core.model.States
import com.onvexa.recorder.core.model.formatClock
import com.onvexa.recorder.wear.R
import com.onvexa.recorder.wear.ui.common.WearList
import com.onvexa.recorder.wear.ui.common.formatWhen

@Composable
fun RecordingsScreen(onOpen: (String) -> Unit, viewModel: RecordingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WearList {
        item {
            Text(
                text = stringResource(R.string.list_title),
                style = MaterialTheme.typography.title3,
                color = OnvexaPalette.Text,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (state.loaded && state.items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.body2,
                    color = OnvexaPalette.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        items(state.items, key = { it.id }) { item ->
            val (icon, tint) = statusIcon(item)
            Chip(
                onClick = { onOpen(item.id) },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = item.title.ifBlank { stringResource(R.string.untitled) },
                        maxLines = 1,
                    )
                },
                secondaryLabel = {
                    Text(text = formatWhen(item.startedAt) + " · " + formatClock(item.durationMs), maxLines = 1)
                },
                icon = icon?.let { vector ->
                    { Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = OnvexaPalette.Surface,
                    contentColor = OnvexaPalette.Text,
                    secondaryContentColor = OnvexaPalette.TextMuted,
                ),
            )
        }
    }
}

private fun statusIcon(item: LibraryItem): Pair<ImageVector?, Color> = when {
    item.status == States.FAILED -> Icons.Default.Warning to OnvexaPalette.Error
    item.pendingSync -> Icons.Default.CloudUpload to OnvexaPalette.Warning
    item.isProcessing -> Icons.Default.HourglassTop to OnvexaPalette.Info
    else -> null to OnvexaPalette.TextMuted
}
