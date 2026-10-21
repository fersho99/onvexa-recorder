package com.onvexa.recorder.core.model

data class MindNode(val title: String, val children: List<MindNode> = emptyList())

/**
 * Nodo ya ubicado. [x] e [y] son la esquina superior izquierda.
 * [branch] es el índice de la rama principal (hijo directo de la raíz); la raíz usa -1.
 */
data class PlacedNode(
    val title: String,
    val depth: Int,
    val branch: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val parentIndex: Int,
)

data class MindMapLayout(val nodes: List<PlacedNode>, val width: Float, val height: Float)

/** Árbol horizontal: la raíz a la izquierda y cada nivel en una columna. */
object MindMapLayouter {

    fun layout(
        root: MindNode,
        measureWidth: (title: String, depth: Int) -> Float,
        nodeHeight: Float = 36f,
        horizontalGap: Float = 48f,
        verticalGap: Float = 12f,
    ): MindMapLayout {
        // Ancho máximo por columna.
        val columnWidth = mutableListOf<Float>()
        fun measure(node: MindNode, depth: Int) {
            val w = measureWidth(node.title, depth)
            if (columnWidth.size <= depth) columnWidth.add(w) else columnWidth[depth] = maxOf(columnWidth[depth], w)
            node.children.forEach { measure(it, depth + 1) }
        }
        measure(root, 0)

        val columnX = FloatArray(columnWidth.size)
        for (d in 1 until columnWidth.size) {
            columnX[d] = columnX[d - 1] + columnWidth[d - 1] + horizontalGap
        }

        val placed = mutableListOf<PlacedNode>()
        var nextLeafY = 0f

        // Devuelve la posición Y del nodo; las hojas se apilan y el padre se centra sobre sus hijos.
        fun place(node: MindNode, depth: Int, branch: Int, parentIndex: Int): Float {
            val index = placed.size
            placed.add(PlacedNode(node.title, depth, branch, columnX[depth], 0f, columnWidth[depth], nodeHeight, parentIndex))
            val y = if (node.children.isEmpty()) {
                val leafY = nextLeafY
                nextLeafY += nodeHeight + verticalGap
                leafY
            } else {
                val childYs = node.children.mapIndexed { i, child ->
                    place(child, depth + 1, if (depth == 0) i else branch, index)
                }
                (childYs.first() + childYs.last()) / 2f
            }
            placed[index] = placed[index].copy(y = y)
            return y
        }
        place(root, 0, -1, -1)

        val totalWidth = columnX.last() + columnWidth.last()
        val totalHeight = (nextLeafY - verticalGap).coerceAtLeast(nodeHeight)
        return MindMapLayout(placed, totalWidth, totalHeight)
    }
}
