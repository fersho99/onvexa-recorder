package com.onvexa.recorder.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.data.ChatMessageView
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.formatClock

@Composable
fun ChatMessages(
    messages: List<ChatMessageView>,
    modifier: Modifier = Modifier,
    onCitation: ((Long) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(messages, key = { it.id }) { message -> Bubble(message, onCitation) }
    }
}

@Composable
private fun Bubble(message: ChatMessageView, onCitation: ((Long) -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (message.isUser) OnvexaPalette.Primary else OnvexaPalette.Surface,
            border = if (message.isUser) null else BorderStroke(1.dp, OnvexaPalette.Outline),
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) OnvexaPalette.OnPrimary else OnvexaPalette.Text,
            )
        }
        if (message.citations.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.citations.forEach { citation ->
                    val chipShape = RoundedCornerShape(8.dp)
                    Surface(
                        modifier = Modifier.clip(chipShape).clickable(enabled = onCitation != null) {
                            onCitation?.invoke(citation.ms)
                        },
                        shape = chipShape,
                        color = OnvexaPalette.PrimaryContainer,
                    ) {
                        Text(
                            text = "▶ " + formatClock(citation.ms),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnvexaPalette.PrimaryLight,
                        )
                    }
                }
            }
        }
    }
}

/** Campo de pregunta deshabilitado hasta que la IA esté conectada. */
@Composable
fun DisabledChatInput(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text(stringResource(R.string.ask_input_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledBorderColor = OnvexaPalette.Outline,
                disabledPlaceholderColor = OnvexaPalette.TextMuted,
                disabledContainerColor = OnvexaPalette.Surface,
            ),
        )
        Text(
            text = stringResource(R.string.ask_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = OnvexaPalette.TextMuted,
        )
    }
}
