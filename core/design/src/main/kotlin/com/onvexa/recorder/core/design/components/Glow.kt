package com.onvexa.recorder.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.core.design.OnvexaPalette

/** Brillo suave alrededor de un elemento redondeado. Requiere API 28+ (el proyecto usa 29+). */
fun Modifier.glow(
    color: Color,
    radius: Dp = 16.dp,
    cornerRadius: Dp = 20.dp,
    alpha: Float = 0.45f,
): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.copy(alpha = 0f).toArgb()
            setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha).toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            cornerRadius.toPx(), cornerRadius.toPx(),
            paint,
        )
    }
}

/** Tarjeta con borde redondeado y brillo sutil, base de la biblioteca y los resúmenes. */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glowColor: Color = OnvexaPalette.Primary,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .glow(glowColor, radius = 14.dp, cornerRadius = 20.dp, alpha = 0.28f)
            .background(OnvexaPalette.Surface, shape)
            .border(BorderStroke(1.dp, OnvexaPalette.Outline), shape)
            .padding(16.dp),
    ) {
        content()
    }
}
