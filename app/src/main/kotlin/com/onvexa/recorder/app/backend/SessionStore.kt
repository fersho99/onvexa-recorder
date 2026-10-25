package com.onvexa.recorder.app.backend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Tokens cifrados con una llave no exportable del Android Keystore. Sin backups. */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("backend_session", Context.MODE_PRIVATE)
    private val alias = "onvexa.session.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): String? = runCatching {
        val value = prefs.getString("encrypted", null) ?: return null
        val parts = value.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
    fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("encrypted", encoded).commit())
    }
    fun clear() { check(prefs.edit().remove("encrypted").commit()) }
    var localOwner: String?
        get() = prefs.getString("local_owner", null)
        set(value) { check(prefs.edit().putString("local_owner", value).commit()) }
    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", false)
        set(value) { check(prefs.edit().putBoolean("sync_enabled", value).commit()) }
}
