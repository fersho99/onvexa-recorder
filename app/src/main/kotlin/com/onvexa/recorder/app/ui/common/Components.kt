package com.onvexa.recorder.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.app.R
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.Source
import com.onvexa.recorder.core.model.States

@Composable
fun SourceBadge(source: Source, modifier: Modifier = Modifier) {
    val icon = if (source == Source.WATCH) Icons.Default.Watch else Icons.Default.PhoneAndroid
    val description = if (source == Source.WATCH) R.string.source_watch else R.string.source_phone
    Box(
        modifier = modifier
            .size(40.dp)
            .background(OnvexaPalette.SurfaceHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = OnvexaPalette.PrimaryLight,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = OnvexaPalette.SurfaceHigh) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = OnvexaPalette.PrimaryLight,
            maxLines = 1,
        )
    }
}

@Composable
fun statusLabel(status: String): String = stringResource(
    when (status) {
        States.RECORDING -> R.string.status_recording
        States.RECORDED, States.UPLOADING, States.UPLOADED -> R.string.status_waiting
        States.TRANSCRIBING -> R.string.status_transcribing
        States.SUMMARIZING -> R.string.status_summarizing
        States.FAILED -> R.string.status_failed
        else -> R.string.status_ready
    },
)

/** Indicador de estado: giratorio mientras se procesa, aviso si falló, nada si está lista. */
@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val processing = status in States.processing || status == States.RECORDING
    val failed = status == States.FAILED
    if (!processing && !failed) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (failed) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = OnvexaPalette.Error, modifier = Modifier.size(14.dp))
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = OnvexaPalette.PrimaryLight,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = if (failed) OnvexaPalette.Error else OnvexaPalette.PrimaryLight,
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OnvexaPalette.Surface,
        border = BorderStroke(1.dp, OnvexaPalette.Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OnvexaPalette.PrimaryLight,
            )
            content()
        }
    }
}

@Composable
fun BulletList(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row {
                Text("•", color = OnvexaPalette.PrimaryLight, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(item, color = OnvexaPalette.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Texto con párrafos y viñetas ("- ") sencillos, como los que genera el resumen. */
@Composable
fun SimpleRichText(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.lines().forEach { line ->
            when {
                line.isBlank() -> Spacer(Modifier.size(2.dp))
                line.startsWith("- ") -> Row {
                    Text("•", color = OnvexaPalette.PrimaryLight, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(line.removePrefix("- "), color = OnvexaPalette.Text, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(line, color = OnvexaPalette.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = OnvexaPalette.PrimaryLight, modifier = Modifier.size(40.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OnvexaPalette.Text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = OnvexaPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun displayTitle(title: String?): String =
    if (title.isNullOrBlank()) stringResource(R.string.untitled) else title
