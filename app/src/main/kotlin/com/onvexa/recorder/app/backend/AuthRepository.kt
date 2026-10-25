package com.onvexa.recorder.app.backend

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class Account(val id: String, val email: String)
private data class Session(val account: Account, val access: String, val refresh: String, val expiresAt: Long)

@Singleton
class AuthRepository @Inject constructor(private val http: BackendHttp, private val store: SessionStore) {
    private val mutex = Mutex()
    private var session: Session? = store.load()?.let { runCatching { parse(Json.parseToJsonElement(it).jsonObject) }.getOrNull() }
    private val _account = MutableStateFlow(session?.account)
    val account = _account.asStateFlow()
    val configured get() = http.configured
    val syncEnabled get() = store.syncEnabled

    suspend fun enableSync() {
        val current = account.value ?: throw BackendException(401, "sign_in_required")
        http.request("PATCH", "/rest/v1/profiles?id=eq.${current.id}", buildJsonObject {
            put("consent_at", java.time.Instant.now().toString())
        }, token())
        withContext(Dispatchers.IO) { store.syncEnabled = true }
    }
    suspend fun disableSync() = withContext(Dispatchers.IO) { store.syncEnabled = false }
    suspend fun forgetLocalOwner() = withContext(Dispatchers.IO) {
        check(session == null)
        store.localOwner = null
    }

    suspend fun signIn(email: String, password: String) = mutex.withLock {
        val response = http.request("POST", "/auth/v1/token?grant_type=password", buildJsonObject {
            put("email", email.trim()); put("password", password)
        }).jsonObject
        accept(response)
    }

    /** true = hay que confirmar el correo; no se manda ningun correo desde el asistente. */
    suspend fun signUp(email: String, password: String): Boolean = mutex.withLock {
        val response = http.request("POST", "/auth/v1/signup", buildJsonObject {
            put("email", email.trim()); put("password", password)
        }).jsonObject
        if (response["access_token"]?.jsonPrimitive?.contentOrNull != null) { accept(response); false } else true
    }

    suspend fun token(): String = mutex.withLock {
        val current = session ?: throw BackendException(401, "sign_in_required")
        if (current.expiresAt > System.currentTimeMillis() / 1000 + 60) return@withLock current.access
        try {
            val response = http.request("POST", "/auth/v1/token?grant_type=refresh_token", buildJsonObject {
                put("refresh_token", current.refresh)
            }).jsonObject
            accept(response)
        } catch (e: BackendException) {
            if (e.status == 400 || e.status == 401) clearSession()
            throw e
        }
        session!!.access
    }

    suspend fun signOut() = mutex.withLock {
        val current = session
        try {
            if (current != null) http.request("POST", "/auth/v1/logout?scope=local", token = current.access)
        } finally { clearSession() }
    }

    suspend fun clearAfterAccountDeletion() = mutex.withLock { clearSession() }

    private suspend fun accept(response: JsonObject) {
        val incoming = parse(response)
        val owner = store.localOwner
        if (owner != null && owner != incoming.account.id) {
            // No mezclar la biblioteca del propietario anterior con otra cuenta.
            try { http.request("POST", "/auth/v1/logout?scope=local", token = incoming.access) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { /* No aceptar la sesion aunque falle la revocacion remota. */ }
            throw BackendException(409, "local_library_owned_by_another_account")
        }
        withContext(Dispatchers.IO) {
            store.localOwner = incoming.account.id
            store.save(JsonObject(response + ("expires_at" to JsonPrimitive(incoming.expiresAt))).toString())
        }
        session = incoming; _account.value = incoming.account
    }
    private suspend fun clearSession() {
        withContext(Dispatchers.IO) { store.clear() }
        session = null; _account.value = null
    }
    private fun parse(value: JsonObject): Session {
        val user = value.getValue("user").jsonObject
        return Session(Account(user.getValue("id").jsonPrimitive.content, user["email"]?.jsonPrimitive?.content.orEmpty()),
            value.getValue("access_token").jsonPrimitive.content, value.getValue("refresh_token").jsonPrimitive.content,
            value["expires_at"]?.jsonPrimitive?.longOrNull ?: (System.currentTimeMillis() / 1000 + value.getValue("expires_in").jsonPrimitive.long))
    }
}
