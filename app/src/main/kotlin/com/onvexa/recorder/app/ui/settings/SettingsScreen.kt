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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val models by viewModel.modelStatus.collectAsStateWithLifecycle()
    val link by viewModel.transferStatus.collectAsStateWithLifecycle()
    val vocabulary by viewModel.vocabulary.collectAsStateWithLifecycle()
    var term by remember { mutableStateOf("") }
    var showLegal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val english = Locale.getDefault().language == "en"
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
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
            AccountSettings(viewModel)
        }

        Section(stringResource(R.string.settings_watch)) {
            Text(
                if (link.connectedNodes > 0) "Dispositivos conectados: ${link.connectedNodes}" else "Sin reloj conectado. Instala ambas apps y empareja el reloj con este teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnvexaPalette.TextMuted,
            )
            OutlinedButton(onClick = viewModel::syncWatch, enabled = !link.busy) { Text("Sincronizar reloj") }
            if (link.error != null) Text("No se pudo transferir. Se reintentará al reconectar.", color = OnvexaPalette.Error)
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
            Text("Transcripción gratuita sin conexión. Descarga una vez el modelo de idioma y el de hablantes por Wi-Fi (aprox. 55–65 MB).", color = OnvexaPalette.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("es" to "Español", "en" to "English").forEach { (code,label) ->
                    OutlinedButton(onClick = { viewModel.downloadModel(code) }, enabled = !models.downloading && code !in models.ready) { Text(if (code in models.ready) "$label ✓" else label) }
                }
            }
            if (models.downloading) Text("Descargando: ${models.bytes / 1_048_576} MB", color = OnvexaPalette.TextMuted)
            if (models.error != null) Text("Falló la descarga. Puedes volver a intentarlo.", color = OnvexaPalette.Error)
            ToggleRow("Generar resumen y mapa con IA al transcribir", settings.autoAi, viewModel::setAutomaticAi)
            Text("Al activarlo autorizas enviar la transcripción a Gemini desde tu cuenta. Requiere sesión y sincronización habilitada; puede consumir la cuota gratuita del proveedor.", color = OnvexaPalette.TextMuted)
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

        Section("Vocabulario personal") {
            androidx.compose.material3.OutlinedTextField(value = term, onValueChange = { term = it }, label = { Text("Nombre o término frecuente") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { viewModel.addTerm(term); term = "" }, enabled = term.isNotBlank()) { Text("Añadir") }
            vocabulary.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.term, modifier = Modifier.weight(1f), color = OnvexaPalette.Text)
                    TextButton(onClick = { viewModel.deleteTerm(entry.id) }) { Text("Quitar") }
                }
            }
        }

        Section(stringResource(R.string.settings_privacy)) {
            OutlinedButton(onClick = { exportLauncher.launch("onvexa-export.zip") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_export))
            }
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
