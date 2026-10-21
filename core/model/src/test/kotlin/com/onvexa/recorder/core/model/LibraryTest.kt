package com.onvexa.recorder.core.model

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    private fun item(
        id: String,
        title: String,
        startedAt: Long,
        source: Source = Source.PHONE,
        tags: List<String> = emptyList(),
        preview: String? = null,
        folder: String? = null,
    ) = LibraryItem(id, title, source, States.READY, 60_000, startedAt, preview, tags, folder, "es")

    private val items = listOf(
        item("1", "Reunión de presupuesto", 1_000, Source.WATCH, listOf("trabajo"), "Se aprobó el presupuesto", "f1"),
        item("2", "Clase de redes", 2_000, Source.PHONE, listOf("escuela")),
        item("3", "Llamada con cliente", 3_000, Source.WATCH, listOf("trabajo", "ventas"), null, "f1"),
    )

    @Test
    fun `busqueda ignora acentos y mayusculas`() {
        val result = items.applyFilter(LibraryFilter(query = "REUNION"))
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `busqueda exige todos los terminos`() {
        assertEquals(listOf("1"), items.applyFilter(LibraryFilter(query = "presupuesto aprobo")).map { it.id })
        assertTrue(items.applyFilter(LibraryFilter(query = "presupuesto redes")).isEmpty())
    }

    @Test
    fun `busqueda encuentra por etiqueta`() {
        assertEquals(listOf("3"), items.applyFilter(LibraryFilter(query = "ventas")).map { it.id })
    }

    @Test
    fun `filtros combinados`() {
        val f = LibraryFilter(source = Source.WATCH, tag = "trabajo", folderId = "f1")
        assertEquals(listOf("1", "3"), items.applyFilter(f).map { it.id })
        assertEquals(listOf("3"), items.applyFilter(f.copy(fromMs = 2_500)).map { it.id })
    }

    @Test
    fun `filtro vacio no esta activo y conserva todo`() {
        assertTrue(!LibraryFilter().isActive)
        assertEquals(3, items.applyFilter(LibraryFilter()).size)
        assertTrue(LibraryFilter(query = "a").isActive)
    }

    @Test
    fun `agrupa por dia de mas reciente a mas antiguo`() {
        val utc = ZoneId.of("UTC")
        val day = 86_400_000L
        val list = listOf(
            item("a", "a", 10 * day + 5_000),
            item("b", "b", 9 * day + 5_000),
            item("c", "c", 10 * day + 9_000),
        )
        val groups = list.groupByDay(utc)
        assertEquals(2, groups.size)
        assertEquals(listOf("c", "a"), groups[0].items.map { it.id })
        assertEquals(listOf("b"), groups[1].items.map { it.id })
        assertTrue(groups[0].date.isAfter(groups[1].date))
    }

    @Test
    fun `estados de procesamiento`() {
        assertTrue(items[0].copy(status = States.TRANSCRIBING).isProcessing)
        assertTrue(!items[0].isProcessing)
        assertTrue(items[0].copy(status = States.FAILED).isFailed)
    }
}
