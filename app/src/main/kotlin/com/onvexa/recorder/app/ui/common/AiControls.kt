package com.onvexa.recorder.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.app.R

@Composable
fun AiFeedback(busy: Boolean, error: String?) {
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (error != null) Text(stringResource(when (error) {
        "sign_in_required" -> R.string.ai_sign_in
        "sync_consent_required" -> R.string.ai_enable_sync
        "demo_stays_local" -> R.string.ai_demo_local
        "quota_exceeded" -> R.string.ai_quota
        "provider_rate_limited" -> R.string.ai_provider_limit
        "provider_auth_error", "provider_model_unavailable", "invalid_gemini_model", "server_not_configured" -> R.string.ai_provider_setup
        else -> R.string.ai_error
    }), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
}
@Composable
fun AiConsent(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ai_title)) },
        text = { Text(stringResource(R.string.ai_consent)) },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.ai_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}
@Composable
fun AiChatInput(busy: Boolean, onSend: (String) -> Unit) {
    var question by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(question, { question = it }, enabled = !busy, modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.ask_input_hint)) }, maxLines = 3)
        TextButton(onClick = { pending = question.trim() }, enabled = !busy && question.isNotBlank() && question.length <= 8000) {
            Text(stringResource(R.string.ai_send))
        }
    }
    if (pending != null) AiConsent(onAccept = { val value = pending!!; pending = null; question = ""; onSend(value) }, onDismiss = { pending = null })
}
