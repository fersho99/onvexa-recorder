package com.onvexa.recorder.wear.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.design.components.RecordButton
import com.onvexa.recorder.core.design.components.RecordButtonKind
import com.onvexa.recorder.wear.R
import com.onvexa.recorder.wear.link.LinkState
import com.onvexa.recorder.wear.ui.common.RoundIconButton

@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onRecordings: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connected = state.link == LinkState.Connected
    val battery = state.battery

    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        TimeText()
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 30.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (connected) OnvexaPalette.Success else OnvexaPalette.TextMuted, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (connected) R.string.home_connected else R.string.home_disconnected),
                    style = MaterialTheme.typography.caption1,
                    color = OnvexaPalette.TextMuted,
                )
                if (battery != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${battery.percent}%",
                        style = MaterialTheme.typography.caption1,
                        color = if (battery.low) OnvexaPalette.Warning else OnvexaPalette.TextMuted,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RecordButton(
                    kind = RecordButtonKind.Record,
                    description = stringResource(R.string.home_record),
                    onClick = onRecord,
                    size = 68.dp,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (battery != null && battery.low) {
                    Text(
                        text = stringResource(R.string.home_battery_low),
                        style = MaterialTheme.typography.caption2,
                        color = OnvexaPalette.Warning,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundIconButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        description = stringResource(R.string.home_recordings),
                        onClick = onRecordings,
                        size = 40.dp,
                    )
                    RoundIconButton(
                        icon = Icons.Default.Settings,
                        description = stringResource(R.string.home_settings),
                        onClick = onSettings,
                        size = 40.dp,
                    )
                }
            }
        }
    }
}
