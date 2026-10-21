package com.onvexa.recorder.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.ui.common.OnvexaChip
import com.onvexa.recorder.core.design.OnvexaPalette
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var showLegal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val english = Locale.getDefault().language == "en"
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = OnvexaPalette.Text,
        )

        Section(stringResource(R.string.settings_account)) {
            Text(
                stringResource(R.string.settings_account_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = OnvexaPalette.TextMuted,
            )
        }

        Section(stringResource(R.string.settings_watch)) {
            Text(
                stringResource(R.string.settings_watch_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = OnvexaPalette.TextMuted,
            )
        }

        Section(stringResource(R.string.settings_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "auto" to R.string.language_auto,
                    "es" to R.string.language_es,
                    "en" to R.string.language_en,
                ).forEach { (code, label) ->
                    OnvexaChip(
                        selected = settings.language == code,
                        onClick = { viewModel.setLanguage(code) },
                        label = stringResource(label),
                    )
                }
            }
            if (!viewModel.languagePerAppSupported) {
                Text(
                    stringResource(R.string.settings_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnvexaPalette.TextMuted,
                )
            }
        }

        Section(stringResource(R.string.settings_template)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates, key = { it.id }) { template ->
                    OnvexaChip(
                        selected = (settings.defaultTemplateId ?: templates.firstOrNull()?.id) == template.id,
                        onClick = { viewModel.setDefaultTemplate(template.id) },
                        label = if (english) template.nameEn else template.nameEs,
                    )
                }
            }
        }

        Section(stringResource(R.string.settings_processing)) {
            ToggleRow(
                label = stringResource(R.string.settings_wifi_only),
                checked = settings.syncWifiOnly,
                onChange = viewModel::setSyncWifiOnly,
            )
            ToggleRow(
                label = stringResource(R.string.settings_charging_only),
                checked = settings.processOnlyWhileCharging,
                onChange = viewModel::setProcessOnlyWhileCharging,
            )
        }

        Section(stringResource(R.string.settings_privacy)) {
            OutlinedButton(onClick = { showLegal = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_legal))
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_delete_all), color = OnvexaPalette.Error)
            }
        }

        if (viewModel.demoAvailable) {
            Section(stringResource(R.string.settings_demo)) {
                Text(
                    stringResource(R.string.settings_demo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnvexaPalette.TextMuted,
                )
                OutlinedButton(onClick = viewModel::seedDemo, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_demo_load))
                }
                OutlinedButton(onClick = viewModel::clearDemo, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_demo_clear))
                }
            }
        }
    }

    if (showLegal) {
        AlertDialog(
            onDismissRequest = { showLegal = false },
            containerColor = OnvexaPalette.Surface,
            title = { Text(stringResource(R.string.settings_legal), color = OnvexaPalette.Text) },
            text = { Text(stringResource(R.string.legal_notice), color = OnvexaPalette.TextMuted) },
            confirmButton = {
                TextButton(onClick = { showLegal = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = OnvexaPalette.Surface,
            title = { Text(stringResource(R.string.settings_delete_all), color = OnvexaPalette.Text) },
            text = { Text(stringResource(R.string.settings_delete_all_message), color = OnvexaPalette.TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAll()
                }) { Text(stringResource(R.string.action_delete), color = OnvexaPalette.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = OnvexaPalette.Surface,
        border = BorderStroke(1.dp, OnvexaPalette.Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnvexaPalette.Text)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = OnvexaPalette.Text,
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnvexaPalette.OnPrimary,
                checkedTrackColor = OnvexaPalette.Primary,
                uncheckedThumbColor = OnvexaPalette.TextMuted,
                uncheckedTrackColor = OnvexaPalette.SurfaceHigh,
            ),
        )
    }
}
