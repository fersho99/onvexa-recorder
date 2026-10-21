package com.onvexa.recorder.app.demo

/**
 * Datos de ejemplo para recorrer la app antes de tener grabaciones reales.
 * Solo existe en las compilaciones debug; la de release usa una implementación vacía.
 */
interface DemoSeeder {
    val available: Boolean

    /** Carga los ejemplos la primera vez que se abre la app. */
    suspend fun seedIfFirstRun()

    /** Carga los ejemplos aunque ya se hubieran cargado antes. */
    suspend fun seed()

    /** Quita únicamente los datos de ejemplo. */
    suspend fun clear()
}
