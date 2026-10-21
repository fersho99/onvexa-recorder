package com.onvexa.recorder.wear.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.formatClock
import com.onvexa.recorder.wear.R
import kotlinx.coroutines.delay

/** Cierre de la grabación: confirma que se guardó y vuelve solo al inicio. */
@Composable
fun SavedScreen(durationMs: Long, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2_200)
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(OnvexaPalette.Success.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = OnvexaPalette.Success, modifier = Modifier.size(26.dp))
            }
            Text(
                text = stringResource(R.string.saved_title),
                style = MaterialTheme.typography.title3,
                color = OnvexaPalette.Text,
            )
            Text(
                text = formatClock(durationMs),
                style = MaterialTheme.typography.body2,
                color = OnvexaPalette.TextMuted,
            )
            Text(
                text = stringResource(R.string.saved_pending),
                style = MaterialTheme.typography.caption2,
                color = OnvexaPalette.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
