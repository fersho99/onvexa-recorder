package com.onvexa.recorder.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.core.design.OnvexaPalette

/**
 * Onda de audio como barras redondeadas de un solo color.
 *
 * @param levels amplitudes normalizadas 0..1, la más reciente al final.
 * @param barWidth ancho de cada barra.
 * @param gap separación entre barras.
 */
@Composable
fun AudioWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth().height(72.dp),
    color: Color = OnvexaPalette.PrimaryLight,
    barWidth: Dp = 4.dp,
    gap: Dp = 3.dp,
    minBarHeight: Dp = 3.dp,
) {
    Canvas(modifier = modifier) {
        val bw = barWidth.toPx()
        val step = bw + gap.toPx()
        val capacity = (size.width / step).toInt().coerceAtLeast(1)
        val visible = levels.takeLast(capacity)
        val totalWidth = visible.size * step - gap.toPx()
        val startX = (size.width - totalWidth).coerceAtLeast(0f)
        val minH = minBarHeight.toPx()
        visible.forEachIndexed { i, raw ->
            val level = raw.coerceIn(0f, 1f)
            val h = (minH + (size.height - minH) * level).coerceAtMost(size.height)
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * step, (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f),
            )
        }
    }
}
