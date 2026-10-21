package com.onvexa.recorder.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `agrega prefijo a cada termino`() {
        assertEquals("reunión* presupuesto*", FtsQuery.sanitize("reunión presupuesto"))
    }

    @Test
    fun `elimina operadores y comillas`() {
        assertEquals("plan* Q4*", FtsQuery.sanitize("\"plan\" - (Q4)"))
    }

    @Test
    fun `texto vacio o solo simbolos no es buscable`() {
        assertFalse(FtsQuery.isSearchable("   "))
        assertFalse(FtsQuery.isSearchable("*-\""))
    }

    @Test
    fun `texto normal es buscable`() {
        assertTrue(FtsQuery.isSearchable("hola"))
    }
}
