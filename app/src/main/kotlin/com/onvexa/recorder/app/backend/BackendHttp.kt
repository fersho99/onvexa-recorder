package com.onvexa.recorder.app.backend

import com.onvexa.recorder.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BackendException(val status: Int, val code: String) : Exception(code)

@Singleton
class BackendHttp @Inject constructor() {
    val configured: Boolean get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    suspend fun request(
        method: String, path: String, payload: JsonElement? = null,
        token: String? = null, headers: Map<String, String> = emptyMap(),
    ): JsonElement = withContext(Dispatchers.IO) {
        check(configured) { "backend_not_configured" }
        require(path.startsWith("/") && !path.startsWith("//"))
        val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val parsed = runCatching { Json.parseToJsonElement(result) }.getOrDefault(JsonNull)
            if (status !in 200..299) {
                val code = runCatching {
                    val obj = parsed.jsonObject
                    (obj["error_code"] ?: obj["code"] ?: obj["error"])?.jsonPrimitive?.content
                }.getOrNull() ?: "http_$status"
                throw BackendException(status, code)
            }
            parsed
        } finally { connection.disconnect() }
    }
}
