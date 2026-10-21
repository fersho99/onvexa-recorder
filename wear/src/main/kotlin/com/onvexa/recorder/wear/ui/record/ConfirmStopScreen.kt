package com.onvexa.recorder.wear.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.wear.R

/** Aparece al intentar salir mientras se graba. */
@Composable
fun ConfirmStopScreen(
    onKeepRecording: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.confirm_title),
            style = MaterialTheme.typography.title3,
            color = OnvexaPalette.Text,
            textAlign = TextAlign.Center,
        )
        Chip(
            onClick = { viewModel.stop(onSaved) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.confirm_stop), maxLines = 1) },
            colors = ChipDefaults.chipColors(backgroundColor = OnvexaPalette.Recording, contentColor = Color.White),
        )
        Chip(
            onClick = onKeepRecording,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.confirm_continue), maxLines = 1) },
            colors = ChipDefaults.chipColors(backgroundColor = OnvexaPalette.SurfaceHigh, contentColor = OnvexaPalette.Text),
        )
    }
}
