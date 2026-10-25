package com.onvexa.recorder.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onvexa.recorder.app.R

@Composable
fun AccountSettings(viewModel: SettingsViewModel) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val state by viewModel.accountStatus.collectAsStateWithLifecycle()
    val sync by viewModel.syncStatus.collectAsStateWithLifecycle()
    val aiPreview by viewModel.aiPreview.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    // No guardar contrasenas en saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmSync by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmAiTest by remember { mutableStateOf(false) }
    LaunchedEffect(account?.id) { password = "" }
    if (!viewModel.backendConfigured) {
        Text(stringResource(R.string.account_not_configured))
        return
    }
    if (account == null) {
        OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.account_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.account_password)) },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.signIn(email, password) }, enabled = !state.busy && email.isNotBlank() && password.isNotBlank()) { Text(stringResource(R.string.account_sign_in)) }
            OutlinedButton(onClick = { viewModel.signUp(email, password) }, enabled = !state.busy && email.isNotBlank() && password.length >= 8) { Text(stringResource(R.string.account_sign_up)) }
        }
    } else {
        Text(account!!.email)
        OutlinedButton(onClick = { confirmAiTest = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ai_test))
        }
        OutlinedButton(onClick = { confirmSync = true }, enabled = !state.busy && !sync.running, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (sync.running) R.string.account_syncing else R.string.account_sync))
        }
        OutlinedButton(onClick = { viewModel.signOut() }, enabled = !state.busy) { Text(stringResource(R.string.account_sign_out)) }
        TextButton(onClick = { confirmDelete = true }, enabled = !state.busy) { Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error) }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    val message = when (state.message) {
        "confirm_email" -> R.string.account_confirm_email
        "signed_in" -> R.string.account_signed_in
        "sync_scheduled" -> R.string.account_sync_scheduled
        "export_done" -> R.string.account_export_done
        "account_deleted" -> R.string.account_deleted
        "local_deleted" -> R.string.account_local_deleted
        "ai_test_success" -> R.string.ai_test_success
        else -> null
    }
    message?.let { Text(stringResource(it)) }
    if (state.message == "ai_test_success" && account != null) aiPreview?.let { Text(it) }
    val error = state.error ?: sync.error
    if (error != null) Text(stringResource(when (error) {
        "invalid_credentials" -> R.string.account_invalid_credentials
        "email_not_confirmed" -> R.string.account_confirm_email
        "local_library_owned_by_another_account" -> R.string.account_other_owner
        "sign_in_required" -> R.string.account_session_expired
        "backend_upgrade_required" -> R.string.account_upgrade_required
        "provider_rate_limited" -> R.string.ai_provider_limit
        "quota_exceeded" -> R.string.ai_quota
        "server_not_configured", "provider_auth_error", "provider_model_unavailable", "invalid_gemini_model" -> R.string.ai_provider_setup
        else -> R.string.account_connection_error
    }), color = MaterialTheme.colorScheme.error)
    if (sync.lastSuccess != null) Text(stringResource(R.string.account_last_sync, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(sync.lastSuccess!!))))
    if (confirmAiTest) AlertDialog(
        onDismissRequest = { confirmAiTest = false }, title = { Text(stringResource(R.string.ai_test)) },
        text = { Text(stringResource(R.string.ai_test_consent)) },
        confirmButton = { TextButton(onClick = { confirmAiTest = false; viewModel.testAi() }) { Text(stringResource(R.string.ai_continue)) } },
        dismissButton = { TextButton(onClick = { confirmAiTest = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirmSync) AlertDialog(
        onDismissRequest = { confirmSync = false }, title = { Text(stringResource(R.string.account_sync)) },
        text = { Text(stringResource(R.string.account_sync_consent)) },
        confirmButton = { TextButton(onClick = { confirmSync = false; viewModel.enableSync() }) { Text(stringResource(R.string.account_sync_accept)) } },
        dismissButton = { TextButton(onClick = { confirmSync = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.account_delete)) },
        text = { Text(stringResource(R.string.account_delete_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteAccount() }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}
