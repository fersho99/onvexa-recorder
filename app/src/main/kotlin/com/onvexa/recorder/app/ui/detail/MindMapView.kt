package com.onvexa.recorder.app.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.MindMapLayouter
import com.onvexa.recorder.core.model.MindNode

/** Mapa mental con zoom y desplazamiento; se dibuja directamente en un Canvas. */
@Composable
fun MindMapView(root: MindNode, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val padX = 14f * density
    val maxText = 200f * density
    val style = remember { TextStyle(fontSize = 13.sp, color = Color.White) }

    val layout = remember(root, density) {
        MindMapLayouter.layout(
            root = root,
            measureWidth = { title, _ ->
                val result = measurer.measure(
                    text = title,
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxText.toInt()),
                )
                result.size.width + padX * 2
            },
            nodeHeight = 38f * density,
            horizontalGap = 44f * density,
            verticalGap = 12f * density,
        )
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember(layout) { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { box ->
                if (!fitted && box.width > 0) {
                    scale = minOf(1f, (box.width - 32f * density) / layout.width).coerceIn(0.3f, 1f)
                    pan = Offset(16f * density, (box.height - layout.height * scale) / 2f)
                    fitted = true
                }
            }
            .pointerInput(layout) {
                detectTransformGestures { centroid, panChange, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.3f, 3f)
                    val applied = newScale / scale
                    pan = (pan - centroid) * applied + centroid + panChange
                    scale = newScale
                }
            },
    ) {
        withTransform({
            translate(pan.x, pan.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            val branches = OnvexaPalette.MindMapBranches
            fun colorOf(branch: Int) = if (branch < 0) OnvexaPalette.PrimaryLight else branches[branch % branches.size]

            // Conexiones primero, para que los nodos queden encima.
            layout.nodes.forEach { node ->
                if (node.parentIndex < 0) return@forEach
                val parent = layout.nodes[node.parentIndex]
                val start = Offset(parent.x + parent.width, parent.y + parent.height / 2f)
                val end = Offset(node.x, node.y + node.height / 2f)
                val mid = (start.x + end.x) / 2f
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(mid, start.y, mid, end.y, end.x, end.y)
                }
                drawPath(path, colorOf(node.branch).copy(alpha = 0.75f), style = Stroke(width = 2f * density))
            }
            layout.nodes.forEach { node ->
                val color = colorOf(node.branch)
                val isRoot = node.depth == 0
                drawRoundRect(
                    color = if (isRoot) OnvexaPalette.Primary else color.copy(alpha = 0.16f),
                    topLeft = Offset(node.x, node.y),
                    size = Size(node.width, node.height),
                    cornerRadius = CornerRadius(node.height / 2f),
                )
                if (!isRoot) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(node.x, node.y),
                        size = Size(node.width, node.height),
                        cornerRadius = CornerRadius(node.height / 2f),
                        style = Stroke(width = 1f * density),
                    )
                }
                val text = measurer.measure(
                    text = node.title,
                    style = style.copy(color = if (isRoot) OnvexaPalette.OnPrimary else OnvexaPalette.Text),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = (node.width - padX * 2).toInt().coerceAtLeast(1)),
                )
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(node.x + padX, node.y + (node.height - text.size.height) / 2f),
                )
            }
        }
    }
}
