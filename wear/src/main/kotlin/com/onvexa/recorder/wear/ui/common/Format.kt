package com.onvexa.recorder.wear.ui.common

import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Hora si es de hoy; fecha corta en otro caso. */
fun formatWhen(ms: Long): String {
    val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (day == LocalDate.now()) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
    }
}
