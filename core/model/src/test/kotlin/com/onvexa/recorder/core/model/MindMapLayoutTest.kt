package com.onvexa.recorder.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MindMapLayoutTest {

    private val tree = MindNode(
        "Junta",
        listOf(
            MindNode("Decisiones", listOf(MindNode("A"), MindNode("B"))),
            MindNode("Pendientes"),
            MindNode("Riesgos", listOf(MindNode("C"))),
        ),
    )

    private val layout = MindMapLayouter.layout(tree, { title, _ -> 10f * title.length + 20f })

    @Test
    fun `ubica todos los nodos en preorden con su padre`() {
        val titles = layout.nodes.map { it.title }
        assertEquals(listOf("Junta", "Decisiones", "A", "B", "Pendientes", "Riesgos", "C"), titles)
        assertEquals(-1, layout.nodes[0].parentIndex)
        assertEquals(0, layout.nodes[1].parentIndex)
        assertEquals(1, layout.nodes[2].parentIndex)
        assertEquals(1, layout.nodes[3].parentIndex)
        assertEquals(0, layout.nodes[4].parentIndex)
        assertEquals(5, layout.nodes[6].parentIndex)
    }

    @Test
    fun `las ramas principales llevan su indice y la raiz -1`() {
        assertEquals(-1, layout.nodes[0].branch)
        assertEquals(listOf(0, 0, 0, 1, 2, 2), layout.nodes.drop(1).map { it.branch })
    }

    @Test
    fun `cada nivel va en una columna a la derecha del anterior`() {
        val x = layout.nodes.groupBy { it.depth }.mapValues { (_, nodes) -> nodes.map { it.x }.distinct() }
        assertTrue(x.values.all { it.size == 1 })
        assertTrue(x.getValue(1).first() > x.getValue(0).first() + layout.nodes[0].width)
        assertTrue(x.getValue(2).first() > x.getValue(1).first())
    }

    @Test
    fun `nodos de una misma columna no se solapan`() {
        for ((_, nodes) in layout.nodes.groupBy { it.depth }) {
            val sorted = nodes.sortedBy { it.y }
            for (i in 1 until sorted.size) {
                assertTrue(sorted[i].y >= sorted[i - 1].y + sorted[i - 1].height)
            }
        }
    }

    @Test
    fun `el padre queda centrado sobre sus hijos`() {
        val parent = layout.nodes[1]
        val a = layout.nodes[2]
        val b = layout.nodes[3]
        assertEquals((a.y + b.y) / 2f, parent.y, 0.001f)
        assertEquals((layout.nodes[1].y + layout.nodes[5].y) / 2f, layout.nodes[0].y, 0.001f)
    }

    @Test
    fun `dimensiones totales contienen todos los nodos`() {
        assertTrue(layout.nodes.all { it.x + it.width <= layout.width + 0.001f })
        assertTrue(layout.nodes.all { it.y + it.height <= layout.height + 0.001f })
    }

    @Test
    fun `un solo nodo`() {
        val single = MindMapLayouter.layout(MindNode("Solo"), { _, _ -> 50f })
        assertEquals(1, single.nodes.size)
        assertEquals(50f, single.width, 0.001f)
        assertEquals(36f, single.height, 0.001f)
    }
}
