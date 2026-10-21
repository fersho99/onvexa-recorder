package com.onvexa.recorder.core.database

/**
 * Convierte texto libre del usuario en una consulta FTS4 segura.
 * Quita operadores y comillas (que romperían MATCH) y aplica prefijo a cada término:
 * "reunión presupuesto" -> "reunión* presupuesto*"
 */
object FtsQuery {
    private val forbidden = Regex("""[^\p{L}\p{N}\s]""")

    fun sanitize(input: String): String =
        input.trim()
            .replace(forbidden, " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }

    fun isSearchable(input: String): Boolean = sanitize(input).isNotEmpty()
}
