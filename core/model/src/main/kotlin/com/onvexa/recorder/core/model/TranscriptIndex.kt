package com.onvexa.recorder.core.model

/**
 * Índice del segmento activo para una posición de audio: el último cuyo inicio es <= [positionMs].
 * [startsMs] debe venir ordenado. Devuelve -1 si la posición es anterior al primer segmento.
 */
fun activeSegmentIndex(startsMs: List<Long>, positionMs: Long): Int {
    var low = 0
    var high = startsMs.size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (startsMs[mid] <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
