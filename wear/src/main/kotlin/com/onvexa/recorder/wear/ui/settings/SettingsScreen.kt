package com.onvexa.recorder.wear.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.wear.R
import com.onvexa.recorder.wear.ui.common.WearList

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val free = Formatter.formatShortFileSize(context, viewModel.freeBytes)
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    WearList {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.title3,
                color = OnvexaPalette.Text,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        item {
            ToggleChip(
                checked = settings.haptics,
                onCheckedChange = viewModel::setHaptics,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_haptics), maxLines = 1) },
                toggleControl = {
                    Icon(imageVector = ToggleChipDefaults.switchIcon(settings.haptics), contentDescription = null)
                },
            )
        }
        item {
            ToggleChip(
                checked = settings.dimWhileRecording,
                onCheckedChange = viewModel::setDim,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_dim), maxLines = 2) },
                secondaryLabel = { Text(stringResource(R.string.settings_dim_hint), maxLines = 2) },
                toggleControl = {
                    Icon(imageVector = ToggleChipDefaults.switchIcon(settings.dimWhileRecording), contentDescription = null)
                },
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_storage, free),
                style = MaterialTheme.typography.caption1,
                color = OnvexaPalette.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.caption2,
                color = OnvexaPalette.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
