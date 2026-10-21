package com.onvexa.recorder.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.core.design.OnvexaPalette

enum class RecordButtonKind { Record, Pause, Resume, Stop }

/**
 * Botón circular grande para el reloj y el celular.
 * Pause/Resume van rellenos en el color de grabación; Record es un punto relleno dentro de un
 * anillo; Stop va con contorno.
 * El ícono se dibuja con Canvas para no depender de librerías de íconos.
 *
 * @param description texto para accesibilidad (TalkBack), ya traducido.
 */
@Composable
fun RecordButton(
    kind: RecordButtonKind,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val ring = kind == RecordButtonKind.Record
    val filled = kind != RecordButtonKind.Stop && !ring
    val iconColor = if (filled) OnvexaPalette.OnRecording else OnvexaPalette.Recording
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .let {
            if (filled) it.background(OnvexaPalette.Recording, CircleShape)
            else it.border(2.dp, OnvexaPalette.Recording, CircleShape)
        }
        .semantics { contentDescription = description }
        .clickable(role = Role.Button, onClick = onClick)

    Box(modifier = base, contentAlignment = Alignment.Center) {
        if (ring) {
            Box(Modifier.size(size * 0.6f).background(OnvexaPalette.Recording, CircleShape))
        } else {
            Canvas(modifier = Modifier.size(size * 0.42f)) { drawIcon(kind, iconColor) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIcon(kind: RecordButtonKind, color: Color) {
    val w = size.width
    val h = size.height
    when (kind) {
        RecordButtonKind.Record -> drawCircle(color, radius = w / 2f)
        RecordButtonKind.Stop -> drawRoundRect(color, size = Size(w, h), cornerRadius = CornerRadius(w * 0.15f))
        RecordButtonKind.Pause -> {
            val barW = w * 0.32f
            drawRoundRect(color, Offset(0f, 0f), Size(barW, h), CornerRadius(barW * 0.3f))
            drawRoundRect(color, Offset(w - barW, 0f), Size(barW, h), CornerRadius(barW * 0.3f))
        }
        RecordButtonKind.Resume -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.1f, 0f)
                lineTo(w, h / 2f)
                lineTo(w * 0.1f, h)
                close()
            }
            drawPath(path, color)
        }
    }
}
