package com.onvexa.recorder.app.data

import com.onvexa.recorder.core.model.MindNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
internal data class MindNodeDto(
    val title: String,
    val children: List<MindNodeDto> = emptyList(),
) {
    fun toModel(): MindNode = MindNode(title, children.map { it.toModel() })
}

/** Momento del audio citado por una respuesta del chat. */
@Serializable
data class Citation(val ms: Long, val text: String = "")

internal fun parseStringList(raw: String): List<String> =
    runCatching { AppJson.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

internal fun parseMindMap(raw: String): MindNode? =
    runCatching { AppJson.decodeFromString<MindNodeDto>(raw).toModel() }.getOrNull()

internal fun parseCitations(raw: String): List<Citation> =
    runCatching { AppJson.decodeFromString<List<Citation>>(raw) }.getOrDefault(emptyList())
