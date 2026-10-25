package com.onvexa.recorder.app.backend

import java.time.Instant
import kotlinx.serialization.json.*

/** Solo tablas de la lista fija; ningun identificador SQL procede de la red. */
object SyncCodec {
    val tables = listOf("folders", "tags", "templates", "recordings", "recording_tags", "bookmarks",
        "speakers", "transcript_segments", "summaries", "action_items", "mind_maps", "attachments",
        "notes", "chat_messages", "vocabulary_terms")
    private val special = mapOf("remoteAudioPath" to "audio_path", "remoteImagePath" to "image_path",
        "keyTopicsJson" to "key_topics", "decisionsJson" to "decisions", "openQuestionsJson" to "open_questions",
        "treeJson" to "tree", "citationsJson" to "citations")
    private val localOnly = setOf("localAudioPath", "localImagePath", "syncState", "remoteUpdatedAt", "isBuiltIn")
    private val times = setOf("createdAt", "updatedAt", "deletedAt", "startedAt")
    fun remoteName(local: String): String = special[local] ?: local.replace(Regex("[A-Z]")) { "_" + it.value.lowercase() }
    fun keys(table: String) = if (table == "recording_tags") listOf("recordingId", "tagId") else listOf("id")
    fun key(table: String, row: JsonObject) = keys(table).joinToString(":") { row.getValue(it).jsonPrimitive.content }
    fun isDemo(table: String, row: JsonObject) = keys(table).any { row.getValue(it).jsonPrimitive.content.startsWith("demo-") }
    fun encode(row: JsonObject, userId: String): JsonObject = buildJsonObject {
        put("user_id", userId)
        for ((name, value) in row) {
            if (name in localOnly || name == "updatedAt") continue
            val converted = when {
                value == JsonNull -> JsonNull
                name in times -> JsonPrimitive(Instant.ofEpochMilli(value.jsonPrimitive.long).toString())
                name in special && name.endsWith("Json") -> Json.parseToJsonElement(value.jsonPrimitive.content)
                else -> value
            }
            put(remoteName(name), converted)
        }
    }
    fun decode(remote: JsonObject, columns: List<String>, previous: JsonObject?): JsonObject = buildJsonObject {
        for (name in columns) {
            val value = remote[remoteName(name)]
            val decoded = when {
                name == "syncState" -> JsonPrimitive(0)
                name == "isBuiltIn" -> JsonPrimitive(if (remote["user_id"] == JsonNull) 1 else 0)
                name == "remoteUpdatedAt" -> JsonPrimitive(Instant.parse(remote.getValue("updated_at").jsonPrimitive.content).toEpochMilli())
                name in localOnly -> previous?.get(name) ?: JsonNull
                value == null || value == JsonNull -> JsonNull
                name in times -> JsonPrimitive(Instant.parse(value.jsonPrimitive.content).toEpochMilli())
                name in special && name.endsWith("Json") -> JsonPrimitive(value.toString())
                else -> value
            }
            put(name, decoded)
        }
    }
}
