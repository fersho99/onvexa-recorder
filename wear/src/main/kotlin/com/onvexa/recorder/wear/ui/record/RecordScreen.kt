package com.onvexa.recorder.wear.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.design.OnvexaType
import com.onvexa.recorder.core.design.components.AudioWaveform
import com.onvexa.recorder.core.design.components.RecordButton
import com.onvexa.recorder.core.design.components.RecordButtonKind
import com.onvexa.recorder.core.model.formatClock
import com.onvexa.recorder.wear.R
import com.onvexa.recorder.wear.recorder.RecorderPhase
import com.onvexa.recorder.wear.ui.common.RoundIconButton

@Composable
fun RecordScreen(
    onRequestExit: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mark by viewModel.mark.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val paused = state.phase == RecorderPhase.Paused

    val context = LocalContext.current
    var consent by remember { mutableStateOf(state.phase == RecorderPhase.Idle) }
    var denied by remember { mutableStateOf(false) }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) { consent = false; viewModel.ensureStarted() }
    }
    if (consent) {
        androidx.wear.compose.material.ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Text("Avisa a los participantes antes de grabar. El audio se guarda en el reloj y se transfiere a tu teléfono.", textAlign = TextAlign.Center) }
            if (denied) item { Text("Permite el micrófono en Ajustes.") }
            item { androidx.wear.compose.material.Chip(onClick = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) { consent = false; viewModel.ensureStarted() }
                else microphone.launch(android.Manifest.permission.RECORD_AUDIO)
            }, label = { Text("Aceptar y grabar") }) }
            item { androidx.wear.compose.material.Chip(onClick = onRequestExit, label = { Text("Cancelar") }) }
        }
        return
    }
    // El gesto de volver no sale de la grabación: pide confirmar.
    BackHandler(onBack = onRequestExit)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        TimeText()
        if (state.error != null || error != null) Text(error ?: "No se pudo grabar. Revisa el permiso y el espacio disponible.", modifier = Modifier.padding(24.dp))
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 30.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Estado o confirmación del resaltado.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(18.dp)) {
                val current = mark
                if (current != null) {
                    Text(
                        text = stringResource(R.string.record_highlight_toast, current.number, formatClock(current.atMs)),
                        style = MaterialTheme.typography.caption1,
                        color = OnvexaPalette.Warning,
                    )
                } else {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(if (paused) OnvexaPalette.TextMuted else OnvexaPalette.RecordingText, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(if (paused) R.string.record_paused else R.string.record_recording),
                        style = MaterialTheme.typography.caption1,
                        color = if (paused) OnvexaPalette.TextMuted else OnvexaPalette.RecordingText,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatClock(state.elapsedMs),
                    style = OnvexaType.Timer.copy(fontSize = 38.sp, lineHeight = 42.sp),
                    color = if (paused) OnvexaPalette.TextMuted else OnvexaPalette.Text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                AudioWaveform(
                    levels = if (paused) emptyList() else state.levels,
                    modifier = Modifier.fillMaxWidth(0.55f).height(26.dp),
                    barWidth = 3.dp,
                    gap = 2.dp,
                    minBarHeight = 2.dp,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    icon = Icons.Default.Bookmark,
                    description = stringResource(R.string.record_highlight),
                    onClick = viewModel::highlight,
                    size = 40.dp,
                    tint = OnvexaPalette.Warning,
                )
                RecordButton(
                    kind = if (paused) RecordButtonKind.Resume else RecordButtonKind.Pause,
                    description = stringResource(if (paused) R.string.record_resume else R.string.record_pause),
                    onClick = viewModel::togglePause,
                    size = 52.dp,
                )
                RecordButton(
                    kind = RecordButtonKind.Stop,
                    description = stringResource(R.string.record_stop),
                    onClick = { viewModel.stop(onSaved) },
                    size = 40.dp,
                )
            }
        }
    }
}

/** Vista atenuada mientras se graba: solo el estado y los minutos, para no gastar batería ni quemar píxeles. */
@Composable
fun AmbientRecording(elapsedMs: Long, paused: Boolean) {
    val minutes = (elapsedMs / 60_000).toInt()
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(8.dp).background(if (paused) Color(0xFF6B7280) else Color(0xFF8E2A2A), CircleShape))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ambient_minutes, minutes),
                style = MaterialTheme.typography.title2,
                color = Color(0xFF8A93A3),
            )
        }
    }
}
