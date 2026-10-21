package com.onvexa.recorder.app.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.ui.detail.TextDialog
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.design.OnvexaType
import com.onvexa.recorder.core.design.components.AudioWaveform
import com.onvexa.recorder.core.design.components.RecordButton
import com.onvexa.recorder.core.design.components.RecordButtonKind
import com.onvexa.recorder.core.model.formatClock
import java.util.Locale

@Composable
fun RecordScreen(onClose: () -> Unit, viewModel: RecordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var noting by remember { mutableStateOf(false) }
    val english = Locale.getDefault().language == "en"

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = OnvexaPalette.Text)
            }
            Spacer(Modifier.weight(1f))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = OnvexaPalette.SurfaceHigh,
        ) {
            Text(
                text = stringResource(R.string.record_preview_notice),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = OnvexaPalette.TextMuted,
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    when {
                        !state.started -> R.string.record_ready
                        state.paused -> R.string.record_paused
                        else -> R.string.record_recording
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (state.started && !state.paused) OnvexaPalette.Recording else OnvexaPalette.TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatClock(state.elapsedMs),
                style = OnvexaType.Timer,
                color = OnvexaPalette.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                if (state.started) {
                    AudioWaveform(levels = state.levels, modifier = Modifier.fillMaxWidth().height(96.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.started) {
                Text(
                    text = stringResource(R.string.record_counters, state.highlights, state.notes),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnvexaPalette.TextMuted,
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates, key = { it.id }) { template ->
                FilterChip(
                    selected = (state.templateId ?: templates.firstOrNull()?.id) == template.id,
                    onClick = { viewModel.selectTemplate(template.id) },
                    label = { Text(if (english) template.nameEn else template.nameEs) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = OnvexaPalette.TextMuted,
                        selectedContainerColor = OnvexaPalette.SurfaceHigh,
                        selectedLabelColor = OnvexaPalette.PrimaryLight,
                    ),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.started) {
                IconButton(onClick = viewModel::highlight) {
                    Icon(Icons.Default.Bookmark, stringResource(R.string.record_highlight), tint = OnvexaPalette.PrimaryLight)
                }
                IconButton(onClick = { noting = true }) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, stringResource(R.string.record_note), tint = OnvexaPalette.PrimaryLight)
                }
                RecordButton(
                    kind = if (state.paused) RecordButtonKind.Resume else RecordButtonKind.Pause,
                    description = stringResource(if (state.paused) R.string.record_resume else R.string.record_pause),
                    onClick = viewModel::togglePause,
                    size = 72.dp,
                )
                RecordButton(
                    kind = RecordButtonKind.Stop,
                    description = stringResource(R.string.record_stop),
                    onClick = {
                        viewModel.stop()
                        onClose()
                    },
                    size = 56.dp,
                )
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.PhotoCamera, stringResource(R.string.record_photo), tint = OnvexaPalette.TextMuted)
                }
            } else {
                RecordButton(
                    kind = RecordButtonKind.Record,
                    description = stringResource(R.string.record_start),
                    onClick = viewModel::start,
                    size = 88.dp,
                )
            }
        }
    }

    if (noting) {
        TextDialog(
            title = stringResource(R.string.record_note_title),
            initial = "",
            onDismiss = { noting = false },
            onConfirm = {
                if (it.isNotBlank()) viewModel.addNote()
                noting = false
            },
        )
    }
}
