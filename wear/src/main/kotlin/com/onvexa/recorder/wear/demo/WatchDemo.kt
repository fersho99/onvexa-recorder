package com.onvexa.recorder.wear.demo

/** Datos de ejemplo del reloj. Solo existen en compilaciones debug; en release no hace nada. */
interface WatchDemoSeeder {
    suspend fun seedIfFirstRun()
}
